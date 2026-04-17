package com.invest.debate.application.service

import com.invest.debate.domain.event.DebateCompletedEvent
import com.invest.debate.domain.event.DebateFailedEvent
import com.invest.debate.domain.model.*
import com.invest.debate.domain.port.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.UUID

@Service
class DebateOrchestrationService(
    private val repository: DebateSessionRepository,
    private val phaseOrchestrator: PhaseOrchestrator,
    private val eventPublisher: EventPublisherPort
) : StartDebateUseCase, GetDebateReportUseCase, RetryDebateUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    // StartDebateUseCase 인터페이스 구현 (REST 직접 호출용 — 세션 생성 + 오케스트레이션 자동 시작)
    override fun startDebate(cmd: StartDebateCommand): Mono<DebateId> =
        startDebateInternal(cmd, externalId = null, autoOrchestrate = true)

    // Kafka Consumer 호출용 — requestId를 debateId로 사용, 오케스트레이션은 Consumer가 별도 실행
    fun startDebate(cmd: StartDebateCommand, externalId: UUID?): Mono<DebateId> =
        startDebateInternal(cmd, externalId = externalId, autoOrchestrate = false)

    private fun startDebateInternal(
        cmd: StartDebateCommand,
        externalId: UUID?,
        autoOrchestrate: Boolean
    ): Mono<DebateId> {
        val session = DebateSession.create(
            userId = cmd.userId,
            ticker = Ticker(cmd.ticker),
            thesis = InvestThesis(cmd.thesis),
            externalId = externalId
        )

        return repository.save(session)
            .flatMap { saved ->
                log.info("[Debate:{}] 세션 생성 완료 — ticker={}", saved.debateId, saved.ticker.value)
                if (autoOrchestrate) {
                    // REST 직접 호출: 별도 스레드에서 오케스트레이션 자동 시작
                    Thread {
                        try {
                            orchestrate(saved.debateId).block()
                            log.info("[Debate:{}] 오케스트레이션 완료", saved.debateId)
                        } catch (e: Exception) {
                            log.error("[Debate:{}] 오케스트레이션 오류: {}", saved.debateId, e.message)
                        }
                    }.start()
                }
                Mono.just(saved.debateId)
            }
    }

    fun orchestrate(debateId: DebateId): Mono<Void> {
        return repository.findById(debateId)
            .flatMap { session ->
                session.startProgress()
                executeWithRetry(session)
            }
            .then()
    }

    private fun executeWithRetry(session: DebateSession): Mono<DebateSession> {
        return phaseOrchestrator.runPhases(session)
            .flatMap { completed ->
                val karpathyResult = completed.phases.find { it.persona == PersonaType.KARPATHY }
                val verdict = karpathyResult?.verdict ?: Verdict.PASS

                if (verdict == Verdict.FAIL) {
                    handleFail(completed)
                } else {
                    handleSuccess(completed)
                }
            }
            .onErrorResume { e ->
                log.error("[Debate:{}] 오류 발생: {}", session.debateId, e.message)
                session.fail()
                repository.save(session).then(Mono.just(session))
            }
    }

    private fun handleSuccess(session: DebateSession): Mono<DebateSession> {
        val report = session.report ?: run {
            val elPhase = session.phases.find { it.persona == PersonaType.EL }
            val fallback = DebateReport(
                consensus = elPhase?.output ?: "합의 도출 완료",
                successProbability = 67,
                disputes = session.phases.flatMap { it.counterArgs }.take(3),
                actions = listOf("Kafka Outbox 프로토타입 생성", "UI 목업 검토", "리스크 리뷰 미팅")
            )
            session.complete(fallback)
            fallback
        }
        log.info("[Debate:{}] 완료 처리 | 성공확률={}%", session.debateId, report.successProbability)

        val verdict = session.phases.find { it.persona == PersonaType.KARPATHY }?.verdict ?: Verdict.PASS

        return repository.save(session)
            .flatMap { saved ->
                val event = DebateCompletedEvent(
                    debateId = saved.debateId,
                    userId = saved.userId,
                    symbol = saved.ticker.value,
                    successProbability = report.successProbability,
                    status = verdict
                )
                eventPublisher.publish(event).thenReturn(saved)
            }
    }

    private fun handleFail(session: DebateSession): Mono<DebateSession> {
        val canRetry = session.handleKarpathyFail()
        return if (canRetry) {
            log.info("[Debate:{}] KARPATHY FAIL — 재시도 #{}", session.debateId, session.retryCount)
            executeWithRetry(session)
        } else {
            log.warn("[Debate:{}] 최대 재시도 초과 — FAILED", session.debateId)
            session.fail()
            repository.save(session)
                .flatMap { saved ->
                    val event = DebateFailedEvent(
                        debateId = saved.debateId,
                        symbol = saved.ticker.value,
                        reason = "KARPATHY_MAX_RETRY"
                    )
                    eventPublisher.publish(event).thenReturn(saved)
                }
        }
    }

    override fun getReport(debateId: DebateId): Mono<DebateReport> {
        return repository.findById(debateId)
            .flatMap { session ->
                if (session.report != null) {
                    Mono.just(session.report!!)
                } else {
                    Mono.error(NoSuchElementException("Report not ready: $debateId"))
                }
            }
    }

    override fun retry(debateId: DebateId): Mono<Void> {
        return repository.findById(debateId)
            .flatMap { session ->
                session.startProgress()
                executeWithRetry(session)
            }
            .then()
    }
}

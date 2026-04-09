package com.invest.debate.domain.port

import com.invest.debate.domain.model.*
import reactor.core.publisher.Mono

// ─── 인바운드 포트 (UseCase) ───────────────────────────

interface StartDebateUseCase {
    fun startDebate(cmd: StartDebateCommand): Mono<DebateId>
}

interface GetDebateReportUseCase {
    fun getReport(debateId: DebateId): Mono<DebateReport>
}

interface RetryDebateUseCase {
    fun retry(debateId: DebateId): Mono<Void>
}

// ─── 커맨드 ───────────────────────────────────────────

data class StartDebateCommand(
    val userId: String,
    val ticker: String,
    val thesis: String,
    val currentPrice: Double?
)

// ─── 아웃바운드 포트 ──────────────────────────────────

interface DebateSessionRepository {
    fun save(session: DebateSession): Mono<DebateSession>
    fun findById(debateId: DebateId): Mono<DebateSession>
    fun updateStatus(debateId: DebateId, status: DebateStatus): Mono<Void>
}

interface LlmPort {
    /**
     * @param persona  호출할 페르소나
     * @param context  이전 Phase 누적 컨텍스트
     * @param ticker   투자 종목
     * @param thesis   투자 thesis
     */
    fun invoke(
        persona: PersonaType,
        context: String,
        ticker: String,
        thesis: String
    ): Mono<String>
}

interface EventPublisherPort {
    fun publish(event: com.invest.debate.domain.event.DebateDomainEvent): Mono<Void>
}

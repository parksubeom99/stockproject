package com.invest.debate.domain.model

import java.time.Instant
import java.util.UUID

class DebateSession private constructor(
    val debateId: DebateId,
    val userId: String,
    val ticker: Ticker,
    val thesis: InvestThesis,
    var status: DebateStatus,
    val phases: MutableList<PhaseResult>,
    var report: DebateReport?,
    val createdAt: Instant,
    var retryCount: Int
) {

    companion object {
        const val MAX_RETRY = 2

        // externalId: Kafka requestId를 debateId로 사용 (RN 폴링 정합성)
        fun create(
            userId: String,
            ticker: Ticker,
            thesis: InvestThesis,
            externalId: UUID? = null
        ): DebateSession = DebateSession(
            debateId = externalId ?: UUID.randomUUID(),
            userId = userId,
            ticker = ticker,
            thesis = thesis,
            status = DebateStatus.REQUESTED,
            phases = mutableListOf(),
            report = null,
            createdAt = Instant.now(),
            retryCount = 0
        )

        fun reconstruct(
            debateId: DebateId,
            userId: String,
            ticker: Ticker,
            thesis: InvestThesis,
            status: DebateStatus,
            phases: MutableList<PhaseResult>,
            report: DebateReport?,
            createdAt: Instant,
            retryCount: Int
        ): DebateSession = DebateSession(
            debateId, userId, ticker, thesis,
            status, phases, report, createdAt, retryCount
        )
    }

    fun startProgress() {
        require(status == DebateStatus.REQUESTED) {
            "Cannot start: current status=$status"
        }
        status = DebateStatus.IN_PROGRESS
    }

    fun addPhaseResult(result: PhaseResult) {
        require(status == DebateStatus.IN_PROGRESS) {
            "Cannot add phase: current status=$status"
        }
        phases.add(result)
    }

    fun handleKarpathyFail(): Boolean {
        retryCount++
        return if (retryCount <= MAX_RETRY) {
            phases.removeAll { it.phaseNum >= 3 }
            true
        } else {
            status = DebateStatus.FAILED
            false
        }
    }

    fun complete(debateReport: DebateReport) {
        require(status == DebateStatus.IN_PROGRESS) {
            "Cannot complete: current status=$status"
        }
        report = debateReport
        status = DebateStatus.COMPLETED
    }

    fun fail() {
        status = DebateStatus.FAILED
    }

    fun buildContext(): String {
        return phases.joinToString("\n\n") { phase ->
            "[${phase.persona.displayName}]\n${phase.output}" +
                if (phase.counterArgs.isNotEmpty())
                    "\n반론: ${phase.counterArgs.joinToString(" / ")}"
                else ""
        }
    }

    fun canRetry(): Boolean = retryCount < MAX_RETRY
}

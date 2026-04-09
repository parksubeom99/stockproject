package com.invest.debate.domain.event

import com.invest.debate.domain.model.DebateId
import java.time.Instant

sealed class DebateDomainEvent {
    abstract val debateId: DebateId
    abstract val occurredAt: Instant
}

data class DebateRequestedEvent(
    override val debateId: DebateId,
    val userId: String,
    val ticker: String,
    val thesis: String,
    val price: Double?,
    override val occurredAt: Instant = Instant.now()
) : DebateDomainEvent()

data class PhaseCompletedEvent(
    override val debateId: DebateId,
    val phaseNum: Int,
    val personaName: String,
    override val occurredAt: Instant = Instant.now()
) : DebateDomainEvent()

data class DebateCompletedEvent(
    override val debateId: DebateId,
    val userId: String,
    val successProbability: Int,
    override val occurredAt: Instant = Instant.now()
) : DebateDomainEvent()

data class DebateFailedEvent(
    override val debateId: DebateId,
    val reason: String,
    override val occurredAt: Instant = Instant.now()
) : DebateDomainEvent()

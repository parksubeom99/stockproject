package com.invest.debate.infrastructure.persistence

import com.invest.debate.domain.model.*
import com.invest.debate.domain.port.DebateSessionRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

/**
 * InMemory 구현체 — Docker Compose 기동 확인용
 * 추후 R2DBC(PostgreSQL) 또는 MongoDB로 교체 예정
 */
@Repository
class InMemoryDebateSessionRepository : DebateSessionRepository {

    private val store = ConcurrentHashMap<DebateId, DebateSession>()

    override fun save(session: DebateSession): Mono<DebateSession> =
        Mono.fromCallable {
            store[session.debateId] = session
            session
        }

    override fun findById(debateId: DebateId): Mono<DebateSession> =
        Mono.fromCallable {
            store[debateId]
                ?: throw NoSuchElementException("DebateSession not found: $debateId")
        }

    override fun updateStatus(debateId: DebateId, status: DebateStatus): Mono<Void> =
        findById(debateId)
            .flatMap { session ->
                session.status = status
                save(session)
            }.then()
}

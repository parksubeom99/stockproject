package com.invest.debate.infrastructure.persistence

import com.invest.debate.domain.model.*
import com.invest.debate.domain.port.DebateSessionRepository
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

/**
 * InMemory 구현체 — test/mock/default 프로파일
 * 실환경(prod)에서는 RedisDebateSessionRepository 사용 (@Profile("prod"))
 */
@Repository
@Profile("!prod")
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

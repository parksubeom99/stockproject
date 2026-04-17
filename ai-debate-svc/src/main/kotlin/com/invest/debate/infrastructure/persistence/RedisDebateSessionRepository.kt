package com.invest.debate.infrastructure.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.invest.debate.domain.model.*
import com.invest.debate.domain.port.DebateSessionRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant

/**
 * Redis 기반 DebateSession 영속성 구현체
 * - TTL 24시간 (토론 데이터는 일회성, 장기 보관 불필요)
 * - DebateSession 내부의 mutable 상태는 Snapshot DTO로 직렬화
 * - Spring Boot AutoConfig의 reactiveStringRedisTemplate 사용 (market-svc 패턴)
 */
@Repository
@Profile("!test")
class RedisDebateSessionRepository(
    @Qualifier("reactiveStringRedisTemplate")
    private val redisTemplate: ReactiveRedisTemplate<String, String>,
    private val objectMapper: ObjectMapper
) : DebateSessionRepository {

    private val log = LoggerFactory.getLogger(javaClass)

    private val keyPrefix = "debate:session:"
    private val ttl = Duration.ofHours(24)

    private fun key(debateId: DebateId) = "$keyPrefix$debateId"

    override fun save(session: DebateSession): Mono<DebateSession> {
        return Mono.fromCallable {
            objectMapper.writeValueAsString(session.toSnapshot())
        }.flatMap { json ->
            redisTemplate.opsForValue()
                .set(key(session.debateId), json, ttl)
                .doOnSuccess {
                    log.debug("[Redis SAVE] debateId={} ttl={}h", session.debateId, ttl.toHours())
                }
                .thenReturn(session)
        }
    }

    override fun findById(debateId: DebateId): Mono<DebateSession> {
        return redisTemplate.opsForValue()
            .get(key(debateId))
            .switchIfEmpty(
                Mono.error(NoSuchElementException("DebateSession not found: $debateId"))
            )
            .map { json ->
                val snapshot = objectMapper.readValue(json, DebateSessionSnapshot::class.java)
                snapshot.toDomain()
            }
    }

    override fun updateStatus(debateId: DebateId, status: DebateStatus): Mono<Void> =
        findById(debateId)
            .flatMap { session ->
                session.status = status
                save(session)
            }.then()
}

/**
 * DebateSession → Jackson 직렬화용 DTO
 * DebateSession은 private constructor라서 직접 역직렬화 불가 → reconstruct() 경유
 */
internal data class DebateSessionSnapshot(
    val debateId: DebateId,
    val userId: String,
    val ticker: String,
    val thesis: String,
    val status: DebateStatus,
    val phases: List<PhaseResult>,
    val report: DebateReport?,
    val createdAt: Instant,
    val retryCount: Int
) {
    fun toDomain(): DebateSession = DebateSession.reconstruct(
        debateId = debateId,
        userId = userId,
        ticker = Ticker(ticker),
        thesis = InvestThesis(thesis),
        status = status,
        phases = phases.toMutableList(),
        report = report,
        createdAt = createdAt,
        retryCount = retryCount
    )
}

internal fun DebateSession.toSnapshot() = DebateSessionSnapshot(
    debateId = debateId,
    userId = userId,
    ticker = ticker.value,
    thesis = thesis.content,
    status = status,
    phases = phases.toList(),
    report = report,
    createdAt = createdAt,
    retryCount = retryCount
)

package com.stockproject.market.infrastructure.redis

import com.fasterxml.jackson.databind.ObjectMapper
import com.stockproject.market.domain.StockQuote
import com.stockproject.market.domain.QuoteCachePort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.Duration

@Component
class RedisQuoteCacheAdapter(
    // @Qualifier로 Spring Boot AutoConfig Bean 명시 지정 → 빈 충돌 제거
    @Qualifier("reactiveStringRedisTemplate")
    private val redisTemplate: ReactiveRedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    @Value("\${market.cache.quote-ttl-seconds:10}") private val ttlSeconds: Long
) : QuoteCachePort {

    private val log = LoggerFactory.getLogger(javaClass)
    private val KEY_PREFIX = "quote:"

    override fun get(symbol: String): Mono<StockQuote> {
        return redisTemplate.opsForValue()
            .get("$KEY_PREFIX$symbol")
            .map { objectMapper.readValue(it, StockQuote::class.java) }
            .doOnError { e -> log.warn("[Cache GET ERR] symbol={}", symbol, e) }
            .onErrorResume { Mono.empty() }
    }

    override fun put(quote: StockQuote): Mono<Void> {
        val json = objectMapper.writeValueAsString(quote)
        return redisTemplate.opsForValue()
            .set("$KEY_PREFIX${quote.symbol}", json, Duration.ofSeconds(ttlSeconds))
            .doOnSuccess { log.debug("[Cache SET] symbol={}, ttl={}s", quote.symbol, ttlSeconds) }
            .then()
    }
}

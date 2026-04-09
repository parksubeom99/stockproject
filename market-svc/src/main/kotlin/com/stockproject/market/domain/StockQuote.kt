package com.stockproject.market.domain

import java.math.BigDecimal
import java.time.Instant

/**
 * 주식 시세 Value Object
 * DDD: 불변, 동일성 = 값 기반
 */
data class StockQuote(
    val symbol: String,
    val price: BigDecimal,
    val change: BigDecimal,
    val changePercent: BigDecimal,
    val volume: Long,
    val timestamp: Instant = Instant.now()
)

/**
 * 토론 요청 이벤트 (Kafka debate.requested 발행용)
 */
data class DebateRequestedEvent(
    val requestId: String,
    val symbol: String,
    val thesis: String,
    val userId: String,
    val requestedAt: Instant = Instant.now()
)

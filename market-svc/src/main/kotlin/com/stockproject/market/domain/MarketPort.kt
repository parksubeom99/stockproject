package com.stockproject.market.domain

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Inbound Port: 유스케이스 인터페이스
 */
interface MarketUseCase {
    fun getQuote(symbol: String): Mono<StockQuote>
    fun streamQuote(symbol: String): Flux<StockQuote>
    fun requestDebate(event: DebateRequestedEvent): Mono<Void>
}

/**
 * Outbound Port: 외부 시세 API 추상화
 */
interface StockQuotePort {
    fun fetchQuote(symbol: String): Mono<StockQuote>
}

/**
 * Outbound Port: 캐시 추상화
 */
interface QuoteCachePort {
    fun get(symbol: String): Mono<StockQuote>
    fun put(quote: StockQuote): Mono<Void>
}

/**
 * Outbound Port: Kafka 발행 추상화
 */
interface DebateEventPort {
    fun publishDebateRequested(event: DebateRequestedEvent): Mono<Void>
}

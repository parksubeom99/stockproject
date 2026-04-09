package com.stockproject.market.application

import com.stockproject.market.domain.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * Market 유스케이스 구현체
 * Hexagonal: Application 계층 — Inbound Port 구현, Outbound Port 주입
 */
@Service
class MarketService(
    private val stockQuotePort: StockQuotePort,
    private val quoteCachePort: QuoteCachePort,
    private val debateEventPort: DebateEventPort
) : MarketUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 시세 조회: Cache → API 폴백 패턴
     */
    override fun getQuote(symbol: String): Mono<StockQuote> {
        return quoteCachePort.get(symbol)
            .doOnNext { log.debug("[Cache HIT] symbol={}", symbol) }
            .switchIfEmpty(
                stockQuotePort.fetchQuote(symbol)
                    .doOnNext { log.info("[API] symbol={}, price={}", it.symbol, it.price) }
                    .flatMap { quote ->
                        quoteCachePort.put(quote).thenReturn(quote)
                    }
            )
            .doOnError { e -> log.error("[getQuote ERROR] symbol={}", symbol, e) }
    }

    /**
     * WebSocket 실시간 스트림: 일정 주기로 시세 polling
     * Phase 2 Step 3에서 WebSocket Handler에 연결
     */
    override fun streamQuote(symbol: String): Flux<StockQuote> {
        return Flux.interval(Duration.ofSeconds(3))
            .flatMap { getQuote(symbol) }
            .distinctUntilChanged { it.price }
            .doOnSubscribe { log.info("[Stream START] symbol={}", symbol) }
            .doOnCancel { log.info("[Stream CANCEL] symbol={}", symbol) }
    }

    /**
     * AI 토론 요청 → Kafka debate.requested 발행
     * PC-12: debate.requested 토픽 방향 = Market svc → AI Debate svc
     */
    override fun requestDebate(event: DebateRequestedEvent): Mono<Void> {
        log.info("[DebateRequest] requestId={}, symbol={}", event.requestId, event.symbol)
        return debateEventPort.publishDebateRequested(event)
    }
}

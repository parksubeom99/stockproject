package com.stockproject.market

import com.stockproject.market.application.MarketService
import com.stockproject.market.domain.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.math.BigDecimal

@DisplayName("MarketService 단위 테스트")
class MarketServiceTest {

    private val stockQuotePort = mockk<StockQuotePort>(relaxed = true)
    private val quoteCachePort = mockk<QuoteCachePort>(relaxed = true)
    private val debateEventPort = mockk<DebateEventPort>(relaxed = true)

    private val sut = MarketService(stockQuotePort, quoteCachePort, debateEventPort)

    @Test
    @DisplayName("getQuote - 캐시 HIT 시 캐시값 반환")
    fun getQuote_cacheHit_returnsCachedValue() {
        val cached = StockQuote("AAPL", BigDecimal("195.00"), BigDecimal("1.00"), BigDecimal("0.51"), 1_000_000)
        every { quoteCachePort.get("AAPL") } returns Mono.just(cached)

        StepVerifier.create(sut.getQuote("AAPL"))
            .expectNext(cached)
            .verifyComplete()

        verify(exactly = 1) { quoteCachePort.get("AAPL") }
    }

    @Test
    @DisplayName("getQuote - 캐시 MISS 시 API 호출 후 캐시 저장")
    fun getQuote_cacheMiss_callsApiAndCaches() {
        val fetched = StockQuote("TSLA", BigDecimal("175.00"), BigDecimal("-2.00"), BigDecimal("-1.13"), 500_000)
        every { quoteCachePort.get("TSLA") } returns Mono.empty()
        every { stockQuotePort.fetchQuote("TSLA") } returns Mono.just(fetched)
        every { quoteCachePort.put(fetched) } returns Mono.empty()

        StepVerifier.create(sut.getQuote("TSLA"))
            .expectNext(fetched)
            .verifyComplete()

        verify(exactly = 1) { stockQuotePort.fetchQuote("TSLA") }
        verify(exactly = 1) { quoteCachePort.put(fetched) }
    }

    @Test
    @DisplayName("getQuote - 외부 API 오류 시 에러 전파")
    fun getQuote_apiError_propagatesError() {
        every { quoteCachePort.get("NVDA") } returns Mono.empty()
        every { stockQuotePort.fetchQuote("NVDA") } returns Mono.error(RuntimeException("API timeout"))

        StepVerifier.create(sut.getQuote("NVDA"))
            .expectError(RuntimeException::class.java)
            .verify()

        verify(exactly = 0) { quoteCachePort.put(any()) }
    }

    @Test
    @DisplayName("streamQuote - 구독 시 시세 스트림 반환")
    fun streamQuote_returns_flux() {
        val quote = StockQuote("AAPL", BigDecimal("196.00"), BigDecimal("1.00"), BigDecimal("0.51"), 1_000_000)
        every { quoteCachePort.get("AAPL") } returns Mono.just(quote)

        StepVerifier.create(sut.streamQuote("AAPL").take(1))
            .expectNextMatches { it.symbol == "AAPL" }
            .verifyComplete()
    }

    @Test
    @DisplayName("requestDebate - Kafka 이벤트 발행 성공")
    fun requestDebate_success() {
        val event = DebateRequestedEvent("req-1", "NVDA", "상승 여력 있음", "user-1")
        every { debateEventPort.publishDebateRequested(event) } returns Mono.empty()

        StepVerifier.create(sut.requestDebate(event))
            .verifyComplete()

        verify(exactly = 1) { debateEventPort.publishDebateRequested(event) }
    }

    @Test
    @DisplayName("requestDebate - Kafka 발행 실패 시 에러 전파")
    fun requestDebate_kafkaError_propagatesError() {
        val event = DebateRequestedEvent("req-2", "AAPL", "고평가 우려", "user-2")
        every { debateEventPort.publishDebateRequested(event) } returns Mono.error(RuntimeException("Kafka unavailable"))

        StepVerifier.create(sut.requestDebate(event))
            .expectError(RuntimeException::class.java)
            .verify()
    }
}
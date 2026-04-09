package com.stockproject.market

import com.stockproject.market.adapter.external.MockStockQuoteAdapter
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.math.BigDecimal

@DisplayName("MockStockQuoteAdapter 단위 테스트")
class MockStockQuoteAdapterTest {

    private val sut = MockStockQuoteAdapter()

    @Test
    @DisplayName("fetchQuote - 등록된 종목(AAPL) 시세 반환 시 양수 가격")
    fun `fetchQuote - 등록된 종목 AAPL 시세 반환`() {
        StepVerifier.create(sut.fetchQuote("AAPL"))
            .expectNextMatches { quote ->
                quote.symbol == "AAPL" &&
                quote.price > BigDecimal.ZERO &&
                quote.volume > 0
            }
            .verifyComplete()
    }

    @Test
    @DisplayName("fetchQuote - 미등록 종목 시 기본가 100.0 기준으로 시세 반환")
    fun `fetchQuote - 미등록 종목 기본가 기준 시세 반환`() {
        StepVerifier.create(sut.fetchQuote("UNKNOWN"))
            .expectNextMatches { quote ->
                quote.symbol == "UNKNOWN" &&
                quote.price > BigDecimal.ZERO
            }
            .verifyComplete()
    }

    @Test
    @DisplayName("fetchQuote - 삼성전자(005930) 시세 반환")
    fun `fetchQuote - 삼성전자 종목코드 시세 반환`() {
        StepVerifier.create(sut.fetchQuote("005930"))
            .expectNextMatches { quote ->
                quote.symbol == "005930" &&
                quote.price > BigDecimal("60000")
            }
            .verifyComplete()
    }
}

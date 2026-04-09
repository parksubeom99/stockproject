package com.stockproject.market.adapter.external

import com.stockproject.market.domain.StockQuote
import com.stockproject.market.domain.StockQuotePort
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.ThreadLocalRandom

/**
 * Gate 2-P: @Profile("mock") — Alpha Vantage API 없이 랜덤 시세 반환
 * 실 API 연동 전 WebSocket/Kafka 구조 검증용
 */
@Component
@Profile("mock")
class MockStockQuoteAdapter : StockQuotePort {

    private val log = LoggerFactory.getLogger(javaClass)

    // 종목별 기준가 (mock)
    private val basePrices = mapOf(
        "AAPL" to 195.0,
        "TSLA" to 175.0,
        "NVDA" to 875.0,
        "005930" to 72000.0,  // 삼성전자
        "000660" to 145000.0  // SK하이닉스
    )

    override fun fetchQuote(symbol: String): Mono<StockQuote> {
        val base = basePrices[symbol] ?: 100.0
        val rand = ThreadLocalRandom.current()
        val price = BigDecimal(base + rand.nextDouble(-base * 0.02, base * 0.02))
            .setScale(2, RoundingMode.HALF_UP)
        val change = price.subtract(BigDecimal(base)).setScale(2, RoundingMode.HALF_UP)
        val changePercent = change.divide(BigDecimal(base), 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)

        log.debug("[MockAPI] symbol={}, price={}", symbol, price)

        return Mono.just(
            StockQuote(
                symbol = symbol,
                price = price,
                change = change,
                changePercent = changePercent,
                volume = rand.nextLong(100_000, 5_000_000)
            )
        )
    }
}

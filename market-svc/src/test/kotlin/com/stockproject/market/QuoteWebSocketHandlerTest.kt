package com.stockproject.market

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.stockproject.market.adapter.web.QuoteWebSocketHandler
import com.stockproject.market.domain.MarketUseCase
import com.stockproject.market.domain.StockQuote
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.socket.HandshakeInfo
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.math.BigDecimal
import java.net.URI

class QuoteWebSocketHandlerTest {

    private val marketUseCase = mockk<MarketUseCase>()
    private val objectMapper = ObjectMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    private val sut = QuoteWebSocketHandler(marketUseCase, objectMapper)

    @Test
    fun `handle - AAPL 시세 스트림을 WebSocket 메시지로 전송`() {
        val quote = StockQuote("AAPL", BigDecimal("196.00"), BigDecimal("1.00"), BigDecimal("0.51"), 1_000_000)
        val session = mockk<WebSocketSession>(relaxed = true)
        val handshakeInfo = mockk<HandshakeInfo>()

        every { session.id } returns "test-session"
        every { session.handshakeInfo } returns handshakeInfo
        every { handshakeInfo.uri } returns URI.create("ws://localhost:8081/ws/quotes/AAPL")
        every { marketUseCase.streamQuote("AAPL") } returns Flux.just(quote)
        every { session.textMessage(any()) } returns mockk<WebSocketMessage>()
        every { session.send(any()) } returns Mono.empty()

        StepVerifier.create(sut.handle(session))
            .verifyComplete()
    }

    @Test
    fun `handle - symbol 대문자 변환 확인`() {
        val quote = StockQuote("TSLA", BigDecimal("175.00"), BigDecimal("-1.00"), BigDecimal("-0.57"), 500_000)
        val session = mockk<WebSocketSession>(relaxed = true)
        val handshakeInfo = mockk<HandshakeInfo>()

        every { session.id } returns "test-session-2"
        every { session.handshakeInfo } returns handshakeInfo
        every { handshakeInfo.uri } returns URI.create("ws://localhost:8081/ws/quotes/tsla")  // 소문자 입력
        every { marketUseCase.streamQuote("TSLA") } returns Flux.just(quote)   // 대문자로 조회
        every { session.textMessage(any()) } returns mockk<WebSocketMessage>()
        every { session.send(any()) } returns Mono.empty()

        StepVerifier.create(sut.handle(session))
            .verifyComplete()
    }
}

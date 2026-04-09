package com.stockproject.market.adapter.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.stockproject.market.domain.MarketUseCase
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono

/**
 * WebSocket 핸들러 — 실시간 시세 스트림
 * 경로: ws://localhost:8081/ws/quotes/{symbol}
 *
 * 섹션 D-1 패턴 준수:
 *   - session.send(Flux<WebSocketMessage>) 연결
 *   - URI에서 symbol 직접 파싱 (path variable 추출)
 *   - Flux 취소는 session.send() 완료 시 자동 처리
 */
@Component
class QuoteWebSocketHandler(
    private val marketUseCase: MarketUseCase,
    private val objectMapper: ObjectMapper
) : WebSocketHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun handle(session: WebSocketSession): Mono<Void> {
        // URI 마지막 세그먼트에서 symbol 추출: /ws/quotes/AAPL → AAPL
        val symbol = session.handshakeInfo.uri.path
            .substringAfterLast("/")
            .uppercase()
            .ifBlank { "AAPL" }

        log.info("[WS CONNECT] sessionId={}, symbol={}", session.id, symbol)

        val messageStream = marketUseCase.streamQuote(symbol)
            .map { quote ->
                val json = objectMapper.writeValueAsString(quote)
                session.textMessage(json)
            }
            .doOnError { e -> log.error("[WS STREAM ERR] symbol={}", symbol, e) }
            .doOnCancel { log.info("[WS CANCEL] sessionId={}, symbol={}", session.id, symbol) }

        return session.send(messageStream)
            .doFinally { log.info("[WS CLOSE] sessionId={}, symbol={}", session.id, symbol) }
    }
}

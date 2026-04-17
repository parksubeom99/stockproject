package com.stockproject.market.infrastructure.config

import com.stockproject.market.adapter.web.QuoteWebSocketHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter

/**
 * WebSocket 엔드포인트 등록.
 * 핸드셰이크 JWT 검증은 WebSocketHandshakeInterceptor(WebFilter)가 "/ws/" 경로를 가로챔.
 */
@Configuration
class WebSocketConfig {

    @Bean
    fun webSocketHandlerMapping(
        quoteWebSocketHandler: QuoteWebSocketHandler
    ): HandlerMapping {
        val urlMap = mapOf(
            "/ws/quotes/*" to quoteWebSocketHandler
        )
        val mapping = SimpleUrlHandlerMapping()
        mapping.urlMap = urlMap
        mapping.order = -1
        return mapping
    }

    @Bean
    fun webSocketHandlerAdapter(): WebSocketHandlerAdapter =
        WebSocketHandlerAdapter()
}

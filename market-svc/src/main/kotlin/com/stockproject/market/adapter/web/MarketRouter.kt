package com.stockproject.market.adapter.web

import com.stockproject.market.domain.DebateRequestedEvent
import com.stockproject.market.domain.MarketUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.*
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * WebFlux Functional Router
 * - GET  /quotes/{symbol}        → 시세 조회 (단건)
 * - GET  /quotes/{symbol}/stream → WebSocket 전환 전 SSE 테스트용
 * - POST /debate/request         → AI 토론 요청 (Kafka 발행)
 * - GET  /health                 → 헬스체크
 */
@Configuration
class MarketRouter {

    @Bean
    fun routes(handler: MarketHandler): RouterFunction<ServerResponse> =
        RouterFunctions.route()
            .GET("/health") { ServerResponse.ok().bodyValue(mapOf("status" to "UP", "service" to "market-svc")) }
            .GET("/quotes/{symbol}", handler::getQuote)
            .GET("/quotes/{symbol}/stream", handler::streamQuote)
            .POST("/debate/request", handler::requestDebate)
            .build()
}

@Configuration
class MarketHandler(private val marketUseCase: MarketUseCase) {

    fun getQuote(req: ServerRequest): Mono<ServerResponse> {
        val symbol = req.pathVariable("symbol").uppercase()
        return marketUseCase.getQuote(symbol)
            .flatMap { ServerResponse.ok().bodyValue(it) }
            .onErrorResume { ServerResponse.status(500).bodyValue(mapOf("error" to it.message)) }
    }

    /**
     * SSE 스트림 — WebSocket 구현 전 테스트용
     * curl -N http://localhost:8081/quotes/AAPL/stream
     */
    fun streamQuote(req: ServerRequest): Mono<ServerResponse> {
        val symbol = req.pathVariable("symbol").uppercase()
        return ServerResponse.ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(marketUseCase.streamQuote(symbol), com.stockproject.market.domain.StockQuote::class.java)
    }

    fun requestDebate(req: ServerRequest): Mono<ServerResponse> {
        return req.bodyToMono(DebateRequestBody::class.java)
            .flatMap { body ->
                val event = DebateRequestedEvent(
                    requestId = UUID.randomUUID().toString(),
                    symbol = body.symbol.uppercase(),
                    thesis = body.thesis,
                    userId = body.userId
                )
                marketUseCase.requestDebate(event)
                    .then(ServerResponse.ok().bodyValue(mapOf("requestId" to event.requestId, "status" to "ACCEPTED")))
            }
            .onErrorResume { ServerResponse.status(500).bodyValue(mapOf("error" to it.message)) }
    }
}

data class DebateRequestBody(
    val symbol: String,
    val thesis: String,
    val userId: String
)

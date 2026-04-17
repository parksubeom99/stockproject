package com.stockproject.market.infrastructure.auth

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * WebSocket 핸드셰이크 JWT 검증 인터셉터
 *
 * 동작:
 * 1. /ws/ 경로 요청만 가로챔 (그 외 경로는 그대로 통과)
 * 2. ?token 쿼리파라미터 추출
 * 3. HS256 서명 검증 → 통과 시 체인 진행, 실패 시 401
 *
 * mock 프로파일 편의:
 * - websocket.dev-token 값("dev-token")이면 서명 검증 없이 허용
 *   (개발/테스트 편의 — prod 프로파일에서는 동작 안 함)
 *
 * WebFlux는 HandshakeInterceptor를 지원하지 않으므로 WebFilter로 구현.
 * HTTP Upgrade 요청이 WebSocketHandler에 도달하기 전에 401 반환 가능.
 */
@Component
class WebSocketHandshakeInterceptor(
    @Value("\${websocket.jwt-secret}") private val jwtSecret: String,
    @Value("\${websocket.dev-token:dev-token}") private val devToken: String,
    private val environment: Environment
) : WebFilter {

    private val log = LoggerFactory.getLogger(javaClass)
    private val secretKey = Keys.hmacShaKeyFor(jwtSecret.toByteArray())
    private val parser = Jwts.parser().verifyWith(secretKey).build()

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val path = exchange.request.path.value()
        if (!path.startsWith("/ws/")) {
            return chain.filter(exchange)
        }

        val token = exchange.request.queryParams.getFirst("token")
        if (token.isNullOrBlank()) {
            log.warn("[WS AUTH] token 누락 path={}", path)
            return unauthorized(exchange)
        }

        if (isMockProfile() && token == devToken) {
            log.debug("[WS AUTH] dev-token bypass (mock profile) path={}", path)
            return chain.filter(exchange)
        }

        return try {
            val claims = parser.parseSignedClaims(token).payload
            log.debug("[WS AUTH] JWT 검증 성공 sub={} path={}", claims.subject, path)
            chain.filter(exchange)
        } catch (e: Exception) {
            log.warn("[WS AUTH] JWT 검증 실패: {} path={}", e.message, path)
            unauthorized(exchange)
        }
    }

    private fun unauthorized(exchange: ServerWebExchange): Mono<Void> {
        exchange.response.statusCode = HttpStatus.UNAUTHORIZED
        return exchange.response.setComplete()
    }

    private fun isMockProfile(): Boolean =
        environment.activeProfiles.any { it == "mock" || it == "test" }
            || environment.activeProfiles.isEmpty()
}

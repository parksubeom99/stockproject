package com.stockproject.market

import com.stockproject.market.infrastructure.auth.WebSocketHandshakeInterceptor
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.env.MockEnvironment
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.Date

@DisplayName("WebSocketHandshakeInterceptor — JWT 핸드셰이크 검증")
class WebSocketHandshakeInterceptorTest {

    private val secret = "stockproject-dev-secret-key-min-32chars-hs256-portfolio!!"
    private val devToken = "dev-token"
    private val secretKey = Keys.hmacShaKeyFor(secret.toByteArray())

    private fun interceptor(profile: String = "mock") = WebSocketHandshakeInterceptor(
        jwtSecret = secret,
        devToken = devToken,
        environment = MockEnvironment().apply { setActiveProfiles(profile) }
    )

    private val passChain = WebFilterChain { Mono.empty() }

    @Test
    @DisplayName("토큰 없으면 401 반환")
    fun `토큰 누락 401`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/ws/quotes/AAPL")
        )

        StepVerifier.create(interceptor().filter(exchange, passChain))
            .verifyComplete()

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
    }

    @Test
    @DisplayName("mock 프로파일에서 dev-token 허용")
    fun `dev-token mock bypass`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/ws/quotes/AAPL?token=dev-token")
        )

        StepVerifier.create(interceptor("mock").filter(exchange, passChain))
            .verifyComplete()

        // 체인이 통과됨 → 응답 status 변경 없음
        assertEquals(null, exchange.response.statusCode)
    }

    @Test
    @DisplayName("prod 프로파일에서 dev-token은 거부 (JWT 파싱 실패 → 401)")
    fun `dev-token prod 차단`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/ws/quotes/AAPL?token=dev-token")
        )

        StepVerifier.create(interceptor("prod").filter(exchange, passChain))
            .verifyComplete()

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
    }

    @Test
    @DisplayName("유효한 HS256 JWT 통과")
    fun `유효한 JWT 통과`() {
        val validJwt = Jwts.builder()
            .subject("test-user")
            .issuedAt(Date())
            .signWith(secretKey)
            .compact()

        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/ws/quotes/AAPL?token=$validJwt")
        )

        StepVerifier.create(interceptor("prod").filter(exchange, passChain))
            .verifyComplete()

        assertEquals(null, exchange.response.statusCode)
    }

    @Test
    @DisplayName("/ws 외 경로는 검증 없이 통과")
    fun `비 WS 경로 bypass`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/quotes/AAPL")
        )

        StepVerifier.create(interceptor().filter(exchange, passChain))
            .verifyComplete()

        assertEquals(null, exchange.response.statusCode)
    }
}

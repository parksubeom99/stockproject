package com.invest.debate.infrastructure.llm

import com.fasterxml.jackson.annotation.JsonProperty
import com.invest.debate.domain.model.PersonaType
import com.invest.debate.domain.port.LlmPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Duration

@org.springframework.context.annotation.Profile("prod")
@Component
class ClaudeApiAdapter(
    @Value("\${anthropic.api-key}") private val apiKey: String,
    @Value("\${anthropic.model:claude-sonnet-4-20250514}") private val model: String
) : LlmPort {

    private val log = LoggerFactory.getLogger(javaClass)

    private val webClient = WebClient.builder()
        .baseUrl("https://api.anthropic.com")
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .defaultHeader("x-api-key", apiKey)
        .defaultHeader("anthropic-version", "2023-06-01")
        .build()

    override fun invoke(
        persona: PersonaType,
        context: String,
        ticker: String,
        thesis: String
    ): Mono<String> {
        val systemPrompt = buildSystemPrompt(persona)
        val request = ClaudeRequest(
            model = model,
            maxTokens = 1000,
            system = systemPrompt,
            messages = listOf(
                ClaudeMessage(role = "user", content = context)
            )
        )

        log.debug("[LLM] {} 호출 시작", persona.displayName)

        return webClient.post()
            .uri("/v1/messages")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(ClaudeResponse::class.java)
            .timeout(Duration.ofSeconds(30))  // Gate 1 위험항목 대응: timeout 설정
            .map { response ->
                response.content.firstOrNull()?.text
                    ?: throw IllegalStateException("Empty response from Claude API")
            }
            .doOnSuccess { log.debug("[LLM] {} 응답 완료 ({}자)", persona.displayName, it.length) }
            .doOnError { e -> log.error("[LLM] {} 호출 실패: {}", persona.displayName, e.message) }
            .retry(1)  // 네트워크 오류 시 1회 재시도
    }

    private fun buildSystemPrompt(persona: PersonaType): String = when (persona) {
        PersonaType.AMODEI ->
            "너는 Dario Amodei (수석 아키텍트). 투자 thesis를 아키텍처 관점으로 초기 분석하고, 리스크 3개와 counterargument 2개를 제시해. 반드시 한국어로 응답."
        PersonaType.ALTMAN ->
            "너는 Sam Altman (현실주의 전략가). 이전 분석을 검토하고 현실성·전략성을 평가해. counterargument 2개 필수. 반드시 한국어로 응답."
        PersonaType.MUSK ->
            "너는 Elon Musk (파괴적 분석가). First principles로 이전까지 의견에 무자비하게 반론 2개 이상 제시하고 대안을 제시해. 반드시 한국어로 응답."
        PersonaType.KARPATHY ->
            "너는 Andrej Karpathy (QA 검증자). 전체 토의를 검증하고 PASS/HOLD/FAIL 판정을 내려. 출력 첫 줄에 반드시 [판정: PASS] 또는 [판정: FAIL] 형식으로 명시. 반드시 한국어로 응답."
        PersonaType.EL ->
            """너는 EL (총괄 비서). 전체 토의를 종합해서 아래 JSON 형식으로만 응답해. 다른 텍스트 절대 금지.
{
  "consensus": "합의사항 요약 (1~2문장)",
  "successProbability": 75,
  "disputes": ["미합의 쟁점1", "미합의 쟁점2"],
  "actions": ["즉시 실행 액션1", "즉시 실행 액션2", "즉시 실행 액션3"]
}"""
    }
}

// ─── Request/Response DTO ───────────────────────────────

data class ClaudeRequest(
    val model: String,
    @JsonProperty("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<ClaudeMessage>
)

data class ClaudeMessage(
    val role: String,
    val content: String
)

data class ClaudeResponse(
    val id: String,
    val type: String,
    val content: List<ContentBlock>,
    val model: String,
    val usage: Usage
)

data class ContentBlock(
    val type: String,
    val text: String
)

data class Usage(
    @JsonProperty("input_tokens") val inputTokens: Int,
    @JsonProperty("output_tokens") val outputTokens: Int
)

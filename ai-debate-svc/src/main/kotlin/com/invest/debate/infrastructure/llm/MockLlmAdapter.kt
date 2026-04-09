package com.invest.debate.infrastructure.llm

import com.invest.debate.domain.model.PersonaType
import com.invest.debate.domain.port.LlmPort
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * Mock LlmPort — API 크레딧 없이 전체 흐름 검증용
 * 실행: spring.profiles.active=mock
 * 실제 API: spring.profiles.active=prod
 */
@Profile("mock")
@Component
class MockLlmAdapter : LlmPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun invoke(
        persona: PersonaType,
        context: String,
        ticker: String,
        thesis: String
    ): Mono<String> {
        log.info("[MOCK LLM] {} 호출 — ticker={}", persona.displayName, ticker)

        // 실제 API 지연 시뮬레이션 (0.5초)
        return Mono.delay(Duration.ofMillis(500))
            .map { buildMockResponse(persona, ticker, thesis) }
            .doOnSuccess {
                log.info("[MOCK LLM] {} 응답 완료", persona.displayName)
            }
    }

    private fun buildMockResponse(persona: PersonaType, ticker: String, thesis: String): String =
        when (persona) {
            PersonaType.AMODEI -> """
                [분석] $ticker 종목에 대한 투자 thesis를 검토했습니다.
                thesis: "$thesis"
                아키텍처 관점에서 투자 리스크를 분석합니다.
                [리스크1] 단기 수급 불균형 — 기관 매도 압력 존재
                [리스크2] 환율 변동성 — 원달러 1400원 이상 시 실적 영향
                [리스크3] 경쟁사 추격 — 시장 점유율 하락 가능성
                [반론1] 현재 밸류에이션이 과도하게 낙관적일 수 있습니다
                [반론2] 단기 촉매 부재로 주가 상승 모멘텀이 약합니다
            """.trimIndent()

            PersonaType.ALTMAN -> """
                [전략평가] AMODEI의 분석을 검토했습니다.
                전략적 관점에서 "$thesis"의 현실성을 평가합니다.
                시장 트렌드와 비즈니스 임팩트를 고려하면 thesis는 중간 수준의 타당성을 가집니다.
                [반론1] 거시경제 불확실성이 개별 종목 thesis를 무력화할 수 있습니다
                [반론2] 투자 타이밍이 thesis의 실현 가능성만큼 중요합니다
            """.trimIndent()

            PersonaType.MUSK -> """
                [반론1] First principles로 보면 이 thesis는 과도한 낙관론에 기반합니다
                실제 데이터를 보십시오 — 수요가 정말 thesis대로 움직이고 있습니까?
                [반론2] 기존 분석들이 놓친 핵심 변수가 있습니다
                경쟁 구도 변화와 기술 대체 가능성을 무시하고 있습니다
                [대안] 분할 매수로 리스크를 분산하고 실적 확인 후 추가 진입하십시오
            """.trimIndent()

            PersonaType.KARPATHY -> """
                [판정: PASS] 전체 토의 QA 완료
                분석 완성도: 양호 (리스크 3개 식별, 반론 4회 이상 발생)
                엣지케이스 검토: 급락 시나리오, 유동성 리스크 검토됨
                [반론] 환율 변수와 글로벌 매크로 영향도가 추가 검토 필요합니다
                전체 설계 품질: 합의 도출 가능 수준
            """.trimIndent()

            PersonaType.EL -> """
                {
                  "consensus": "$ticker 투자 thesis는 중간 수준의 타당성 보유. 단기 리스크 관리 하에 분할 접근 권장",
                  "successProbability": 68,
                  "disputes": ["단기 모멘텀 부재 여부", "환율 영향도 수준"],
                  "actions": ["분할 매수 3회 — 현재가 기준 5% 간격 진입", "손절선 현재가 -8% 설정 후 엄수", "다음 실적 발표일 전 포지션 50% 익절 검토"]
                }
            """.trimIndent()
        }
}

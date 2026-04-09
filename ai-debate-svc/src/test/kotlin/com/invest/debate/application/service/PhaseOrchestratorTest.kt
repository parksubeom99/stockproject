package com.invest.debate.application.service

import com.invest.debate.domain.model.*
import com.invest.debate.domain.port.LlmPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@DisplayName("PhaseOrchestrator — 5인 토의 순차 실행 테스트")
class PhaseOrchestratorTest {

    private val llmPort: LlmPort = mockk()
    private lateinit var orchestrator: PhaseOrchestrator

    @BeforeEach
    fun setUp() {
        orchestrator = PhaseOrchestrator(llmPort)
    }

    private fun createTestSession(): DebateSession = DebateSession.create(
        userId = "user-001",
        ticker = Ticker("005930"),
        thesis = InvestThesis("삼성전자 HBM 수요 급증으로 3Q 실적 개선 예상")
    ).also { it.startProgress() }

    @Test
    @DisplayName("정상 흐름: 5인 순차 실행 후 COMPLETED 상태")
    fun `5인 토의 정상 완료 시 COMPLETED 반환`() {
        // given
        mockLlmSuccess()

        val session = createTestSession()

        // when & then
        StepVerifier.create(orchestrator.runPhases(session))
            .assertNext { result ->
                assertEquals(DebateStatus.COMPLETED, result.status)
                assertEquals(5, result.phases.size)
                assertNotNull(result.report)
                assertEquals(3, result.report!!.actions.size)
            }
            .verifyComplete()

        // 5개 페르소나 모두 호출됐는지 검증
        verify(exactly = 5) { llmPort.invoke(any(), any(), any(), any()) }
    }

    @Test
    @DisplayName("KARPATHY PASS: 재시도 없이 완료")
    fun `KARPATHY PASS 판정 시 재시도 없음`() {
        mockLlmSuccess()
        val session = createTestSession()

        StepVerifier.create(orchestrator.runPhases(session))
            .assertNext { result ->
                val karpathy = result.phases.find { it.persona == PersonaType.KARPATHY }
                assertEquals(Verdict.PASS, karpathy?.verdict)
                assertEquals(0, result.retryCount)
            }
            .verifyComplete()
    }

    @Test
    @DisplayName("KARPATHY FAIL 판정: 출력에 FAIL 포함 시 Verdict.FAIL 설정")
    fun `KARPATHY FAIL 판정 추출`() {
        // given — KARPATHY만 FAIL 출력
        every {
            llmPort.invoke(match { it != PersonaType.KARPATHY }, any(), any(), any())
        } returns Mono.just("[분석] 정상 분석 내용\n[반론1] 반론 내용\n[반론2] 반론 내용")

        every {
            llmPort.invoke(PersonaType.KARPATHY, any(), any(), any())
        } returns Mono.just("[판정: FAIL] 설계 불완전 — 리스크 미해결\n[반론] 재시도 필요")

        val session = createTestSession()

        StepVerifier.create(orchestrator.runPhases(session))
            .assertNext { result ->
                val karpathy = result.phases.find { it.persona == PersonaType.KARPATHY }
                assertEquals(Verdict.FAIL, karpathy?.verdict)
            }
            .verifyComplete()
    }

    @Test
    @DisplayName("LLM 타임아웃: 오류 전파 확인")
    fun `LLM 호출 오류 시 Mono error 전파`() {
        every {
            llmPort.invoke(any(), any(), any(), any())
        } returns Mono.error(RuntimeException("Claude API timeout"))

        val session = createTestSession()

        StepVerifier.create(orchestrator.runPhases(session))
            .expectErrorMatches { it.message?.contains("timeout") == true }
            .verify()
    }

    @Test
    @DisplayName("buildContext: Phase 누적 컨텍스트 형식 확인")
    fun `buildContext 누적 형식 검증`() {
        val session = createTestSession()
        session.addPhaseResult(
            PhaseResult(1, PersonaType.AMODEI, "아모데이 분석 내용", listOf("반론1", "반론2"))
        )

        val context = session.buildContext()

        assertTrue(context.contains("Dario Amodei"))
        assertTrue(context.contains("아모데이 분석 내용"))
        assertTrue(context.contains("반론"))
    }

    // ─── 헬퍼 ───────────────────────────────────────────

    private fun mockLlmSuccess() {
        every {
            llmPort.invoke(PersonaType.AMODEI, any(), any(), any())
        } returns Mono.just("[분석] HBM 수요 실제로 증가 중\n[리스크1] 재고 조정\n[리스크2] 환율\n[리스크3] 경쟁사\n[반론1] 단기 조정 가능\n[반론2] 밸류에이션 고평가")

        every {
            llmPort.invoke(PersonaType.ALTMAN, any(), any(), any())
        } returns Mono.just("[전략평가] 현실적 접근 필요\n[반론1] 시장 타이밍\n[반론2] 포지션 크기")

        every {
            llmPort.invoke(PersonaType.MUSK, any(), any(), any())
        } returns Mono.just("[반론1] 과도한 낙관론\n[반론2] First principles — 실제 수요 데이터 부족\n[대안] 분할 매수")

        every {
            llmPort.invoke(PersonaType.KARPATHY, any(), any(), any())
        } returns Mono.just("[판정: PASS] 분석 완성도 양호\n[반론] 환율 변수 추가 검토 권장")

        every {
            llmPort.invoke(PersonaType.EL, any(), any(), any())
        } returns Mono.just("""
            {
              "consensus": "HBM 수요 성장 유효하나 단기 재고 조정 리스크 존재",
              "successProbability": 72,
              "disputes": ["단기 조정 폭", "환율 영향도"],
              "actions": ["분할 매수 3회 진입", "목표가 82000 손절 70000 설정", "3Q 실적 발표 전 포지션 재검토"]
            }
        """.trimIndent())
    }
}

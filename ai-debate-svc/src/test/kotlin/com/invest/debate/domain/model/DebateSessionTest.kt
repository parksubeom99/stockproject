package com.invest.debate.domain.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("DebateSession — 도메인 로직 단위 테스트")
class DebateSessionTest {

    private fun createSession() = DebateSession.create(
        userId = "user-001",
        ticker = Ticker("005930"),
        thesis = InvestThesis("삼성전자 3Q 실적 개선 예상")
    )

    @Test
    @DisplayName("세션 생성 시 REQUESTED 상태")
    fun `초기 상태는 REQUESTED`() {
        val session = createSession()
        assertEquals(DebateStatus.REQUESTED, session.status)
        assertEquals(0, session.retryCount)
        assertTrue(session.phases.isEmpty())
        assertNull(session.report)
    }

    @Test
    @DisplayName("startProgress: REQUESTED → IN_PROGRESS")
    fun `startProgress 상태 전이 성공`() {
        val session = createSession()
        session.startProgress()
        assertEquals(DebateStatus.IN_PROGRESS, session.status)
    }

    @Test
    @DisplayName("startProgress: IN_PROGRESS 상태에서 재호출 시 예외")
    fun `이미 IN_PROGRESS 상태에서 startProgress 예외`() {
        val session = createSession()
        session.startProgress()
        assertThrows<IllegalArgumentException> { session.startProgress() }
    }

    @Test
    @DisplayName("KARPATHY FAIL 첫 번째: retryCount=1, Phase 3+ 제거, 재시도 가능")
    fun `KARPATHY FAIL 첫 번째 재시도 가능`() {
        val session = createSession().also { it.startProgress() }

        // Phase 1~4 추가
        PersonaType.values().take(4).forEachIndexed { idx, persona ->
            session.addPhaseResult(PhaseResult(idx + 1, persona, "출력$idx"))
        }

        val canRetry = session.handleKarpathyFail()

        assertTrue(canRetry)
        assertEquals(1, session.retryCount)
        // Phase 1, 2만 남아야 함
        assertEquals(2, session.phases.size)
        assertTrue(session.phases.all { it.phaseNum <= 2 })
    }

    @Test
    @DisplayName("KARPATHY FAIL MAX_RETRY 초과: FAILED 상태, 재시도 불가")
    fun `MAX_RETRY 초과 시 FAILED`() {
        val session = createSession().also { it.startProgress() }

        // 2회 실패
        repeat(DebateSession.MAX_RETRY) {
            PersonaType.values().take(4).forEachIndexed { idx, persona ->
                session.addPhaseResult(PhaseResult(idx + 1, persona, "출력"))
            }
            session.handleKarpathyFail()
        }

        // 3번째 실패
        val canRetry = session.handleKarpathyFail()

        assertFalse(canRetry)
        assertEquals(DebateStatus.FAILED, session.status)
    }

    @Test
    @DisplayName("complete: report 설정 + COMPLETED 상태")
    fun `complete 후 COMPLETED 상태`() {
        val session = createSession().also { it.startProgress() }
        val report = DebateReport(
            consensus = "합의 내용",
            successProbability = 75,
            disputes = listOf("쟁점1"),
            actions = listOf("액션1", "액션2", "액션3")
        )

        session.complete(report)

        assertEquals(DebateStatus.COMPLETED, session.status)
        assertEquals(75, session.report?.successProbability)
    }

    @Test
    @DisplayName("DebateReport actions 3개 아니면 예외")
    fun `actions 3개 아니면 IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            DebateReport(
                consensus = "합의",
                successProbability = 70,
                disputes = emptyList(),
                actions = listOf("액션1", "액션2")  // 2개 → 예외
            )
        }
    }

    @Test
    @DisplayName("Ticker 빈 값 예외")
    fun `빈 Ticker 예외`() {
        assertThrows<IllegalArgumentException> { Ticker("") }
    }
}

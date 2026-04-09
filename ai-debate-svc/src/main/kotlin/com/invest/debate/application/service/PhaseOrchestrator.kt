package com.invest.debate.application.service

import com.invest.debate.domain.model.*
import com.invest.debate.domain.port.LlmPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class PhaseOrchestrator(
    private val llmPort: LlmPort
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 페르소나별 시스템 프롬프트
    private fun buildSystemPrompt(persona: PersonaType): String = when (persona) {
        PersonaType.AMODEI ->
            """너는 Dario Amodei (수석 아키텍트).
            |강점: DoD 설계 + 리스크 3개 + 시스템 안정성.
            |역할: 투자 thesis에 대해 아키텍처 관점 초기 분석 + 리스크 3개 제시 + counterargument 2개.
            |출력 형식: [분석] [리스크1/2/3] [반론1/2]
            |반드시 한국어로 응답.""".trimMargin()

        PersonaType.ALTMAN ->
            """너는 Sam Altman (현실주의 전략가).
            |강점: 전략적 현실 분석 + 시장 트렌드.
            |역할: 이전 분석을 검토 후 현실성·전략성 평가 + counterargument 2개.
            |이전 발언만 참조해서 반론·보완.
            |출력 형식: [전략평가] [반론1/2]
            |반드시 한국어로 응답.""".trimMargin()

        PersonaType.MUSK ->
            """너는 Elon Musk (파괴적 분석가).
            |강점: First principles + 극단적 효율 + 대담한 반론.
            |역할: 이전까지 의견에 무자비하게 반론 2개 이상 + 대안 제시.
            |이전 발언만 참조해서 반론·보완.
            |출력 형식: [반론1/2] [대안]
            |반드시 한국어로 응답.""".trimMargin()

        PersonaType.KARPATHY ->
            """너는 Andrej Karpathy (QA 검증자).
            |강점: 엣지케이스 + 성능 병목 + 무결성 검증.
            |역할: 전체 토의 QA — PASS/HOLD/FAIL 판정 + 판정 근거 + counterargument.
            |출력 형식: [판정: PASS/HOLD/FAIL] [근거] [반론]
            |반드시 한국어로 응답.""".trimMargin()

        PersonaType.EL ->
            """너는 EL (총괄 비서).
            |강점: 전체 종합 + 합의 도출 + 실행 액션.
            |역할: 전체 토의 종합 → 합의사항 + 성공확률(0~100 정수) + 미합의 쟁점 + 액션 정확히 3개.
            |출력 형식(JSON):
            |{
            |  "consensus": "합의사항 요약",
            |  "successProbability": 75,
            |  "disputes": ["쟁점1", "쟁점2"],
            |  "actions": ["액션1", "액션2", "액션3"]
            |}
            |반드시 JSON만 출력. 다른 텍스트 금지.""".trimMargin()
    }

    /**
     * Phase 1~5 순차 실행
     * KARPATHY FAIL 시 재시도 여부는 호출자(DebateOrchestrationService)가 결정
     */
    fun runPhases(session: DebateSession): Mono<DebateSession> {
        val phases = listOf(
            PersonaType.AMODEI,
            PersonaType.ALTMAN,
            PersonaType.MUSK,
            PersonaType.KARPATHY
        )

        return phases.fold(Mono.just(session)) { acc, persona ->
            acc.flatMap { s -> runSinglePhase(s, persona) }
        }.flatMap { s -> runElPhase(s) }
    }

    private fun runSinglePhase(session: DebateSession, persona: PersonaType): Mono<DebateSession> {
        val context = session.buildContext()
        val userPrompt = buildUserPrompt(session, context)

        log.info("[Debate:{}] Phase {} {} 시작", session.debateId, persona.phaseNum, persona.displayName)

        return llmPort.invoke(
            persona = persona,
            context = userPrompt,
            ticker = session.ticker.value,
            thesis = session.thesis.content
        ).map { output ->
            val counterArgs = extractCounterArgs(output)
            val verdict = if (persona == PersonaType.KARPATHY) extractVerdict(output) else null

            val result = PhaseResult(
                phaseNum = persona.phaseNum,
                persona = persona,
                output = output,
                counterArgs = counterArgs,
                verdict = verdict
            )
            session.addPhaseResult(result)
            log.info("[Debate:{}] Phase {} 완료 | verdict={}", session.debateId, persona.phaseNum, verdict)
            session
        }
    }

    private fun runElPhase(session: DebateSession): Mono<DebateSession> {
        val fullContext = session.buildContext()

        log.info("[Debate:{}] Phase 5 EL 최종 보고 시작", session.debateId)

        return llmPort.invoke(
            persona = PersonaType.EL,
            context = fullContext,
            ticker = session.ticker.value,
            thesis = session.thesis.content
        ).map { output ->
            val report = parseElOutput(output)
            session.addPhaseResult(
                PhaseResult(
                    phaseNum = 5,
                    persona = PersonaType.EL,
                    output = output
                )
            )
            session.complete(report)
            log.info(
                "[Debate:{}] 완료 | 성공확률={}%",
                session.debateId, report.successProbability
            )
            session
        }
    }

    private fun buildUserPrompt(session: DebateSession, context: String): String {
        val contextSection = if (context.isBlank()) "이전 발언 없음" else context
        return """
            |[투자 대상] ${session.ticker.value}
            |[투자 thesis] ${session.thesis.content}
            |
            |[이전 발언]
            |$contextSection
        """.trimMargin()
    }

    // 출력에서 반론 추출 (간단한 규칙 기반)
    private fun extractCounterArgs(output: String): List<String> {
        return output.lines()
            .filter { it.contains("반론") || it.contains("Counter") || it.contains("[반론") }
            .take(2)
    }

    // KARPATHY 판정 추출
    private fun extractVerdict(output: String): Verdict {
        val upper = output.uppercase()
        return when {
            upper.contains("FAIL") -> Verdict.FAIL
            upper.contains("HOLD") || upper.contains("보류") -> Verdict.HOLD
            else -> Verdict.PASS
        }
    }

    // EL JSON 파싱
    // Claude API는 JSON 앞뒤로 자연어를 붙이는 경우가 있으므로
    // 정규식으로 JSON 블록만 추출한 뒤 파싱
    private fun parseElOutput(output: String): DebateReport {
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()

        // 1단계: 중괄호 블록 추출 시도
        val jsonCandidate = runCatching {
            val start = output.indexOf('{')
            val end = output.lastIndexOf('}')
            if (start >= 0 && end > start) output.substring(start, end + 1) else output.trim()
        }.getOrDefault(output.trim())

        return try {
            val node = mapper.readTree(jsonCandidate)
            val actions = node["actions"]?.map { it.asText() } ?: emptyList()
            DebateReport(
                consensus = node["consensus"]?.asText() ?: "합의 내용 파싱 실패",
                successProbability = node["successProbability"]?.asInt()?.coerceIn(0, 100) ?: 50,
                disputes = node["disputes"]?.map { it.asText() } ?: emptyList(),
                // actions 정확히 3개 보장
                actions = when {
                    actions.size >= 3 -> actions.take(3)
                    actions.isNotEmpty() -> actions + List(3 - actions.size) { "추가 액션 ${it + 1} 필요" }
                    else -> listOf("포지션 재검토 권장", "리스크 관리 실행", "추가 분석 진행")
                }
            )
        } catch (e: Exception) {
            log.warn("[EL] JSON 파싱 실패 — fallback 적용. 원본 길이={}자, 오류={}", output.length, e.message)
            DebateReport(
                consensus = "AI 위원회가 분석을 완료했습니다. 상세 결과는 로그를 확인하세요.",
                successProbability = 50,
                disputes = listOf("EL 응답 파싱 실패 — 재시도 권장"),
                actions = listOf("수동 결과 확인 필요", "재시도 권장", "로그 점검")
            )
        }
    }
}

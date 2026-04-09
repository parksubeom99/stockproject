package com.invest.debate.domain.model

import java.util.UUID

// Value Object: 종목 티커
data class Ticker(val value: String) {
    init {
        require(value.isNotBlank()) { "Ticker must not be blank" }
        require(value.length <= 20) { "Ticker must be 20 chars or less" }
    }
}

// Value Object: 투자 thesis
data class InvestThesis(val content: String) {
    init {
        require(content.isNotBlank()) { "Thesis must not be blank" }
        require(content.length <= 1000) { "Thesis must be 1000 chars or less" }
    }
}

// Value Object: Phase 결과
data class PhaseResult(
    val phaseNum: Int,
    val persona: PersonaType,
    val output: String,
    val counterArgs: List<String> = emptyList(),
    val verdict: Verdict? = null  // KARPATHY 전용
)

// Value Object: 최종 레포트
data class DebateReport(
    val consensus: String,
    val successProbability: Int,       // 0~100
    val disputes: List<String>,        // 미합의 쟁점
    val actions: List<String>          // 액션 정확히 3개
) {
    init {
        require(actions.size == 3) { "Actions must be exactly 3" }
        require(successProbability in 0..100) { "Probability must be 0~100" }
    }
}

// 타입 별칭
typealias DebateId = UUID

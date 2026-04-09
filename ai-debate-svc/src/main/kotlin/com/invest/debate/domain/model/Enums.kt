package com.invest.debate.domain.model

enum class DebateStatus {
    REQUESTED,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

enum class PersonaType(val displayName: String, val phaseNum: Int) {
    AMODEI("Dario Amodei", 1),
    ALTMAN("Sam Altman", 2),
    MUSK("Elon Musk", 3),
    KARPATHY("Andrej Karpathy", 4),
    EL("EL Secretary", 5)
}

enum class Verdict {
    PASS,
    HOLD,
    FAIL
}

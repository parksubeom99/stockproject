# ADR-0009: KARPATHY 재시도 = Saga compensating transaction

- **Status**: Accepted
- **Date**: 2026-05-13
- **Deciders**: 박수범 (단독 작업)

## Context

KARPATHY는 phase 4의 QA 검증자 페르소나. `PASS` / `HOLD` / `FAIL` 판정을 내림. `FAIL`은 "이전 토론이 신뢰 가능 수준 아님" 시그널.

`FAIL` 시 대응 옵션의 trade-off:
- **즉시 FAILED 종료**: 회복 기회 없음. 사용자 입장에서 "분석 실패" 만 보임.
- **처음부터 전체 재실행**: AMODEI·ALTMAN은 thesis 자체 분석이라 재실행해도 결과 거의 같음 → API 비용·시간 낭비 (Haiku 5번 호출 = 약 50~125초).
- **무한 재실행**: API 비용 폭주, 사용자 무한 대기.

## Decision

**Saga 패턴 (compensating transaction)** — 신뢰 가능한 phase는 보존, 실패 지점만 되감기:

- `DebateSession.handleKarpathyFail()`:
  1. `retryCount++`
  2. `phases.removeAll { it.phaseNum >= 3 }` — MUSK(phase 3), KARPATHY(phase 4) 제거
  3. MUSK부터 다시 실행
- **`MAX_RETRY = 2` 상한** — 도메인 상수로 명시. 한도 초과 시 `status = FAILED` + `DebateFailedEvent(reason = "KARPATHY_MAX_RETRY")` Kafka 발행 → 다운스트림에서 알림·통계 처리.

AMODEI(phase 1), ALTMAN(phase 2)은 thesis 자체 분석이라 재실행해도 결과가 거의 같으므로 **신뢰 가능한 phase로 분류**하여 보존.

## Consequences

### Positive
- 분산 트랜잭션의 Saga 패턴 — 부분 보존 + 부분 재실행으로 비용·시간 절감 (재실행 시 약 30~50초 절약).
- 명시적 상한 `MAX_RETRY = 2` 로 무한 루프 차단 → API 비용 가드.
- `DebateFailedEvent` 발행으로 다운스트림 컨슈머가 통계/알람 추적 가능.

### Negative / Trade-offs
- `MAX_RETRY` 도달 후 사용자에게 명확한 안내 부재 — RN UI에서 "분석 실패" 단일 메시지 (재시도 횟수·이유 노출 안 됨). UX 개선 잔여.
- 재시도 동안 RN polling은 계속 `IN_PROGRESS` 상태로 보임 — 진행도 노출 없음.
- AMODEI·ALTMAN 보존 가정이 항상 성립하진 않음 — 매우 드물게 초기 분석이 잘못된 thesis 해석을 했으면 무용한 phase가 누적될 수 있음.

## Alternatives Considered

- **즉시 FAILED 종료** — 회복 기회 없음, UX 최악. 기각.
- **전체 재실행** — 비용·시간 낭비, AMODEI·ALTMAN 결과 동일성 가정 시 무의미. 기각.
- **무한 재실행** — API 비용 폭주. 기각.
- **재시도 정책을 KARPATHY 외부에서 결정** (orchestration service에서 모든 retry 관리) — `DebateSession` 애그리거트의 invariant 책임이 분산됨. DDD 원칙 위반. 기각.

## References

- 코드:
  - [DebateSession.kt:19, 69-78](../ai-debate-svc/src/main/kotlin/com/invest/debate/domain/model/DebateSession.kt) (`MAX_RETRY` 상수 + `handleKarpathyFail()`)
  - [DebateOrchestrationService.kt:68-134](../ai-debate-svc/src/main/kotlin/com/invest/debate/application/service/DebateOrchestrationService.kt) (`executeWithRetry` · `handleFail` · `DebateFailedEvent` 발행)
- 관련 ADR: [0003 (DDD + Hexagonal)](0003-ddd-hexagonal.md), [0002 (Kafka 비동기 분리)](0002-kafka-async-decoupling.md)

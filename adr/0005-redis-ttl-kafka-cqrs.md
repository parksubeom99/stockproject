# ADR-0005: Redis 24h TTL + Kafka 이벤트 영속화 (가벼운 CQRS)

- **Status**: Accepted
- **Date**: 2026-05-13
- **Deciders**: 박수범 (단독 작업)

## Context

`DebateSession`은 토론 진행 중 자주 update — `status`, `phases`, `retryCount` 변경이 phase마다 발생. 사용자가 결과를 보고 의사결정에 쓰는 일회성 산출물이며, 1년 뒤 같은 세션을 다시 조회할 가능성은 매우 낮음.

그러나 토론 결과 시그널(`successProbability`, `Verdict`, 실패 사유)은 백테스트·BI·통계 용도로 장기 보관 가치가 있음 — 다운스트림 컨슈머가 자기 store에 쌓을 수 있어야 함.

## Decision

**책임 분리** — 휘발(세션 raw) / 영속(결과 시그널):

- **세션 raw**: Redis에 JSON snapshot으로 저장. 키 `debate:session:{debateId}`, TTL 24시간 자동 만료. `DebateSessionSnapshot` DTO를 거쳐 직렬화/역직렬화.
- **결과 시그널**: `DebateCompletedEvent`(successProbability, status) / `DebateFailedEvent`(reason) 를 Kafka `debate.completed` / `debate.failed` 토픽으로 발행. 다운스트림이 자기 스키마로 적재.

가벼운 CQRS 패턴 — write side는 Redis snapshot(애그리거트), read side는 Kafka event stream(시그널).

## Consequences

### Positive
- Redis 메모리 절약 — 24h 자동 정리로 풍선 효과 차단.
- 휘발/영속 책임이 코드 위치로 명확 분리됨.
- 다운스트림 스키마 자유 — Kafka 컨슈머가 통계용·BI용·백테스트용을 각자 결정.

### Negative / Trade-offs
- 24h 후 사용자가 status polling 시 만료된 세션은 `IN_PROGRESS`로 노출됨 — `DebateController.getStatus` 가 `NoSuchElementException` 을 IN_PROGRESS로 fallback하기 때문. 만료 vs 진행 중 구분 부재 — STEP 5 RUNBOOK 잔여 (`COMPLETED` 캐시 별도 보관 검토).
- 세션 디버깅 raw 로그가 24h 후 사라짐 — 별도 archival 필요 시 Kafka 컨슈머 추가.

## Alternatives Considered

- **영구 보관 RDBMS(PostgreSQL)** — 데이터 99% 미사용 + 비용 증가. 기각.
- **무 TTL Redis** — 메모리 풍선 효과. 기각.
- **Redis snapshot + RDBMS 이중 저장** — 일관성 관리 복잡도 증가. 현 단계에 과함.

## References

- 코드:
  - [RedisDebateSessionRepository.kt:33-68, 74-108](../ai-debate-svc/src/main/kotlin/com/invest/debate/infrastructure/persistence/RedisDebateSessionRepository.kt) (TTL · Snapshot DTO)
  - [KafkaEventPublisher.kt:19-65](../ai-debate-svc/src/main/kotlin/com/invest/debate/infrastructure/kafka/KafkaEventPublisher.kt) (debate.completed/failed 발행)
  - [DebateController.kt:46-53](../ai-debate-svc/src/main/kotlin/com/invest/debate/infrastructure/web/DebateController.kt) (status fallback)
- 관련 ADR: [0002 (Kafka 비동기 분리)](0002-kafka-async-decoupling.md), [0007 (DebateSession 캡슐화)](0007-debatesession-private-constructor.md)

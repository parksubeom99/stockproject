# ADR-0007: `DebateSession` private constructor + `create` / `reconstruct` 팩토리

- **Status**: Accepted
- **Date**: 2026-05-13
- **Deciders**: 박수범 (단독 작업)

## Context

`DebateSession`은 mutable 애그리거트 — `status`, `retryCount`, `phases`, `report` 가 진행 중 변경됨.

두 가지 생성 시나리오가 invariant 측면에서 다름:
- **신규 생성**: `status = REQUESTED`, `retryCount = 0`, `createdAt = now`, `phases = []` 강제 필요.
- **Redis 복원**: 이미 `IN_PROGRESS`/`phases` 차있음/`retryCount > 0` 상태일 수 있음. 그대로 받아야 함.

public constructor를 열면 application 코드가 실수로 두 시나리오를 섞을 위험 — 예: 신규 세션을 IN_PROGRESS로 만들어버림.

## Decision

private constructor + 정적 팩토리 2개로 시나리오 분리:

- `create(userId, ticker, thesis, externalId)` — 신규 세션. invariant(status=REQUESTED, retryCount=0, createdAt=now) 강제. `externalId`는 Kafka requestId를 debateId로 재사용하기 위한 옵션.
- `reconstruct(debateId, ..., status, phases, report, createdAt, retryCount)` — Redis snapshot에서 복원. 모든 필드를 그대로 받음. invariant 검증 없음.

Jackson 직렬화는 별도 DTO `DebateSessionSnapshot` 을 거침 — `toSnapshot()` / `toDomain()` 변환 함수가 사이를 잇고, `toDomain()` 내부에서 `DebateSession.reconstruct(...)` 호출.

## Consequences

### Positive
- **컴파일 타임 가드** — invariant 우회가 컴파일러 수준에서 차단.
- 신규/복원 책임이 호출 사이트에서 명확.
- ORM 없이도 도메인 캡슐화 유지 — JPA 같은 reflection-기반 ORM에 의존 없음.

### Negative / Trade-offs
- DTO ↔ 도메인 변환 보일러플레이트 (`toSnapshot()`, `toDomain()`) — phases / report 같은 복합 타입에서 라인 수 증가.
- JPA 채택 시 reflection이 private constructor를 인식하지 못해 호환 깨짐 — 현재는 Jackson + Redis라 무관, 향후 RDBMS 도입 시 재검토.

## Alternatives Considered

- **public constructor + setter** — 캡슐화 완전 깨짐. 기각.
- **단일 팩토리 + 분기 파라미터** — `create(..., isNew: Boolean)` 같은 형태. 책임 혼란, invariant 검증 분기가 복잡해짐. 기각.
- **Builder 패턴** — 두 시나리오 분리에 과함. setter 누락 시 invariant 우회 가능성. 기각.

## References

- 코드:
  - [DebateSession.kt:18-52](../ai-debate-svc/src/main/kotlin/com/invest/debate/domain/model/DebateSession.kt) (private constructor + 2 factories)
  - [RedisDebateSessionRepository.kt:74-108](../ai-debate-svc/src/main/kotlin/com/invest/debate/infrastructure/persistence/RedisDebateSessionRepository.kt) (`DebateSessionSnapshot` DTO + `toDomain()`)
- 관련 ADR: [0003 (DDD + Hexagonal)](0003-ddd-hexagonal.md), [0005 (Redis snapshot)](0005-redis-ttl-kafka-cqrs.md)

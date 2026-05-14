# ADR-0003: DDD + Hexagonal 아키텍처

- **Status**: Accepted
- **Date**: 2026-05-13
- **Deciders**: 박수범 (단독 작업)

## Context

ai-debate-svc는 외부 의존이 많음 — Claude API, Redis, Kafka, RN HTTP 클라이언트. 그러나 단위 테스트는 빠르고 외부 의존 없이 standalone으로 돌아야 함.

도메인 로직(5인 페르소나 순차 실행, KARPATHY 재시도, EL JSON 파싱, 상태 전이 invariant)이 외부 인프라 변경의 영향을 받지 않도록 격리 필요.

## Decision

DDD + Hexagonal(Ports & Adapters) 패키지 구조 채택:

- `domain/` — 엔티티(`DebateSession` 애그리거트), 값 객체(`Ticker`, `InvestThesis`, `PhaseResult`, `DebateReport`), 포트 인터페이스(`StartDebateUseCase`, `DebateSessionRepository`, `LlmPort`, `EventPublisherPort`)
- `application/` — `@Service` 클래스(`DebateOrchestrationService`, `PhaseOrchestrator`)가 인바운드 포트 구현 + 아웃바운드 포트 호출
- `infrastructure/` — 외부 의존 어댑터 (Kafka, Redis, WebClient, REST Controller)

`DebateSession` 애그리거트는 invariant를 자기 안에 가둠 — `require(status == DebateStatus.REQUESTED)` 같은 가드가 도메인 메서드 진입 시 실행.

## Consequences

### Positive
- 도메인 코어가 Spring · Redis · Kafka · Claude API에 의존 없음 → 단위 테스트가 standalone (MockK + reactor-test로 충분).
- 어댑터 교체 자유 — [ADR-0004 (프로파일 스왑)](0004-profile-based-adapter-swap.md) 참조.
- 상태 전이 invariant가 도메인에 박혀있어 application 레이어 실수 컴파일/런타임 차단.

### Negative / Trade-offs
- 패키지·클래스 수 증가, 단순 CRUD에는 과한 구조.
- 의도된 차등 — market-svc는 hexagonal을 얕게 적용(`MarketService` 단일 application, `MarketPort` 단일 outbound port). 단순 quote 스트리밍에 DDD 전면 적용은 불필요.

## Alternatives Considered

- **레이어드 아키텍처** (Controller → Service → Repository) — 도메인-인프라 경계가 흐려져 invariant가 application·infrastructure에 흩어짐. 기각.
- **트랜잭션 스크립트** — 도메인 invariant(상태 전이 가드, retry count 한도) 표현 어려움. 기각.

## References

- 코드:
  - [Ports.kt](../ai-debate-svc/src/main/kotlin/com/invest/debate/domain/port/Ports.kt) (인바운드/아웃바운드 포트 정의)
  - [DebateSession.kt:55-90](../ai-debate-svc/src/main/kotlin/com/invest/debate/domain/model/DebateSession.kt) (애그리거트 invariant)
  - [DebateOrchestrationService.kt](../ai-debate-svc/src/main/kotlin/com/invest/debate/application/service/DebateOrchestrationService.kt) (application 레이어)
- 관련 ADR: [0004 (프로파일 스왑)](0004-profile-based-adapter-swap.md), [0007 (DebateSession 캡슐화)](0007-debatesession-private-constructor.md)

# ADR-0004: 프로파일 기반 어댑터 스왑 (vs config flag)

- **Status**: Accepted
- **Date**: 2026-05-13
- **Deciders**: 박수범 (단독 작업)

## Context

ai-debate-svc는 외부 의존이 환경마다 달라야 함:

- `LlmPort`: prod에서 실제 Claude API 호출, mock/test에서는 canned response (500ms 지연)
- `DebateSessionRepository`: prod에서 Redis 영속, !prod에서는 ConcurrentHashMap 기반 in-memory

문제: mock 환경에서 `ClaudeApiAdapter`를 빈으로 로드하면 `@Value("\${anthropic.api-key}")` 가 비어 부팅 자체가 실패. 즉 **빈 생성 자체를 환경별로 분리해야 함**.

## Decision

Spring `@Profile` 어노테이션으로 빈 등록을 환경별로 분리:

| 포트 | prod | mock / !prod |
|---|---|---|
| `LlmPort` | `ClaudeApiAdapter` `@Profile("prod")` | `MockLlmAdapter` `@Profile("mock")` |
| `DebateSessionRepository` | `RedisDebateSessionRepository` `@Profile("prod")` | `InMemoryDebateSessionRepository` `@Profile("!prod")` |

Docker Compose:
- `ai-debate-svc` → `SPRING_PROFILES_ACTIVE=prod` (실 Claude + Redis)
- `market-svc` → `SPRING_PROFILES_ACTIVE=mock` (synthetic quote)

기본값(`application.yml`)은 `mock` — 로컬 개발 시 별도 env 없이 standalone 기동 가능.

## Consequences

### Positive
- **부팅 시점 환경 검증** — mock 환경에서 prod 빈이 아예 로드 안 되므로 `ANTHROPIC_API_KEY` 없어도 부팅 정상.
- 어댑터 클래스가 자기 컨텍스트(prod 전용 코드)에만 집중. config flag 분기 보일러플레이트 없음.
- 단위 테스트가 `@ActiveProfiles("test")` 만으로 어댑터 통째 갈아끼움.

### Negative / Trade-offs
- 새 프로파일 추가(예: `integration` for testcontainers) 시 모든 어댑터 어노테이션 검토 필요 — 누락 시 빈 충돌 발생.
- testcontainers 통합 테스트 프로파일은 STEP 4 잔여 항목.

## Alternatives Considered

- **`@ConditionalOnProperty`** — 빈 생성 후 분기. 환경 검증 시점이 늦음(런타임 시점 실패), 어댑터 클래스 자체에 환경 의존 로직이 섞임. 기각.
- **팩토리 빈** — `@Configuration` 안에서 분기 → 보일러플레이트 증가, 어노테이션 기반 자동 스캔 이점 상실. 기각.

## References

- 코드:
  - [ClaudeApiAdapter.kt:15](../ai-debate-svc/src/main/kotlin/com/invest/debate/infrastructure/llm/ClaudeApiAdapter.kt) `@Profile("prod")`
  - [MockLlmAdapter.kt:16](../ai-debate-svc/src/main/kotlin/com/invest/debate/infrastructure/llm/MockLlmAdapter.kt) `@Profile("mock")`
  - [RedisDebateSessionRepository.kt:23](../ai-debate-svc/src/main/kotlin/com/invest/debate/infrastructure/persistence/RedisDebateSessionRepository.kt) `@Profile("prod")`
  - [InMemoryDebateSessionRepository.kt:15](../ai-debate-svc/src/main/kotlin/com/invest/debate/infrastructure/persistence/InMemoryDebateSessionRepository.kt) `@Profile("!prod")`
  - [docker-compose.yml:101-133](../docker-compose.yml) (SPRING_PROFILES_ACTIVE 환경변수)
- 관련 ADR: [0003 (DDD + Hexagonal)](0003-ddd-hexagonal.md)

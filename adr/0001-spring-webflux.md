# ADR-0001: Spring WebFlux 채택 (vs Spring MVC)

- **Status**: Accepted
- **Date**: 2026-05-13
- **Deciders**: 박수범 (단독 작업)

## Context

stockProject의 두 백엔드 서비스는 모두 IO-bound 성격이 강함:

- **market-svc**: WebSocket으로 quote를 3초 주기 스트리밍. RN 클라이언트 다수 동시 연결 가정.
- **ai-debate-svc**: Claude API를 페르소나당 평균 15초, 5번 순차 호출 → 전체 50~125초 long-tail.

Spring MVC + Servlet 스레드 모델은 connection·호출당 스레드를 점유하므로, WebSocket 다중 연결과 long-running API 호출 양쪽에서 스레드 풀 고갈 위험.

## Decision

Spring WebFlux + Reactor(`Mono`/`Flux`) + Netty event-loop 채택. WebClient(non-blocking)로 Claude API 호출.

## Consequences

### Positive
- WebSocket connection N개에 스레드 N개를 쓰지 않음 → 동일 자원으로 더 많은 RN 클라이언트 수용.
- Claude API 호출 동안 스레드 idle → 5단 phase 체인이 스레드 풀 점유 안 함.
- `Mono` flatMap 체인이 `PhaseOrchestrator.runPhases`의 5단 순차 호출과 자연스럽게 매핑.

### Negative / Trade-offs
- Stack trace가 끊김 → 디버깅 학습 곡선. `doOnSuccess` / `doOnError` 로깅으로 부분 보강.
- Spring MVC에 익숙한 엔지니어 영입 시 온보딩 비용 증가.

## Alternatives Considered

- **Spring MVC + RestTemplate** — WebSocket 다중 연결 + 90s Claude API 호출에서 스레드 풀 고갈 우려. 기각.
- **Kotlin Coroutine** — 검토 가치 있으나 Spring 6 시점에선 WebFlux + Reactor가 더 성숙하고 Spring Boot 자동 설정 풍부. 차기 검토.

## References

- 코드:
  - [PhaseOrchestrator.kt:65-76](../ai-debate-svc/src/main/kotlin/com/invest/debate/application/service/PhaseOrchestrator.kt)
  - [ClaudeApiAdapter.kt:24-62](../ai-debate-svc/src/main/kotlin/com/invest/debate/infrastructure/llm/ClaudeApiAdapter.kt)
- 관련 ADR: [0008 (수동 ack)](0008-kafka-manual-ack-thread.md), [0006 (Haiku)](0006-haiku-llm-model.md)

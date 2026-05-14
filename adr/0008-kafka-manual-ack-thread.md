# ADR-0008: Kafka consumer 수동 ack + 별도 스레드 오케스트레이션

- **Status**: Accepted
- **Date**: 2026-05-13
- **Deciders**: 박수범 (단독 작업)

## Context

ai-debate-svc의 Kafka consumer가 `debate.requested` 토픽 메시지를 받음. 메시지 처리 = `DebateSession` 생성 + 5 페르소나 오케스트레이션 = 50~125초 long-running.

문제: consumer 콜백 안에서 오케스트레이션을 그대로 block하면 `KafkaListener` poll loop가 정지됨. Kafka는 `session.timeout.ms`(기본 10초) 초과 시 consumer를 죽은 것으로 판단 → rebalance → 메시지 재처리 폭주 → 동일 토론 중복 실행.

## Decision

세 단계로 분리:

1. **sync 단계 (빠름)**: `objectMapper.readValue` 로 메시지 파싱 → `DebateSession.create(externalId=...)` 호출 → `repository.save(session)` 까지 동기 처리. 실패 시 ack 안 함 → 메시지 자동 재처리.
2. **`ack.acknowledge()` 호출**: offset 명시 커밋. Kafka 설정 `enable-auto-commit: false`.
3. **별도 스레드**: `Thread { orchestrationService.orchestrate(debateId).block() }.start()` 로 오케스트레이션을 콜백 밖으로 떼서 실행. poll loop는 즉시 다음 메시지로 진행.

## Consequences

### Positive
- poll loop 블로킹 없음 → rebalance 회피 → 메시지 중복 처리 차단.
- 세션 생성 단계 실패 시 ack 안 함 → 자동 재처리, at-least-once 보장.
- consumer poll 주기 안정적 → lag 모니터링이 신뢰 가능.

### Negative / Trade-offs
- **raw `Thread` 는 production-grade 아님** — 스레드 누수 가능성, 모니터링 없음. `@Async + TaskExecutor` 또는 Kotlin Coroutine scope로 교체 권장 (STEP 5 RUNBOOK 잔여).
- 오케스트레이션 실패 시 알림 채널 없음 — 로그만 기록. 알람 통합 필요.

## Alternatives Considered

- **자동 커밋 + 콜백 안 처리** — long-running과 양립 불가. rebalance 폭주. 기각.
- **별도 워크 큐 도입 (DB queue / Redis Stream)** — Kafka가 이미 있어서 중복. 기각.
- **Reactor `subscribeOn(Schedulers.boundedElastic())`** — WebFlux 자연스럽지만 KafkaListener 콜백과 Reactor 컨텍스트 통합이 추가 작업. 현 단계에선 단순 Thread 우선.

## References

- 코드:
  - [DebateRequestedConsumer.kt:24-67](../ai-debate-svc/src/main/kotlin/com/invest/debate/infrastructure/kafka/DebateRequestedConsumer.kt) (수동 ack + Thread 분리)
  - [application.yml:23](../ai-debate-svc/src/main/resources/application.yml) (`enable-auto-commit: false`)
- 관련 ADR: [0002 (Kafka 비동기 분리)](0002-kafka-async-decoupling.md), [0001 (WebFlux)](0001-spring-webflux.md)

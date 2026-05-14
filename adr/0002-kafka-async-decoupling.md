# ADR-0002: Kafka 비동기 분리 (vs REST 동기)

- **Status**: Accepted
- **Date**: 2026-05-13
- **Deciders**: 박수범 (단독 작업)

## Context

RN → market-svc → ai-debate-svc 흐름에서 ai-debate-svc 응답 시간 = 5 페르소나 × 평균 15초 = 50~125초 long-tail.

동기 REST 직결의 문제:
- RN 사용자가 1분+ HTTP 응답 대기 → 모바일 환경에서 timeout / 이탈
- market-svc 자원이 응답 대기 동안 점유됨
- Claude API rate limit 발동 시 상류(market-svc)로 장애 전파

## Decision

- market-svc → Kafka `debate.requested` topic publish → ai-debate-svc consumer 비동기 처리
- market-svc는 즉시 `requestId` 반환
- RN은 `GET /debate/{id}/status` 를 3초 주기 polling, `COMPLETED` 응답 시 `GET /debate/{id}/report` 호출
- producer의 `requestId`(UUID)를 consumer가 `externalId`로 받아 `DebateSession.create(externalId=...)` 에 주입 → debateId 정합성 보장

## Consequences

### Positive
- 응답 지연을 producer 쪽에서 절단 → market-svc는 즉시 응답
- service 간 backpressure 분리 — Claude API rate limit이 market-svc에 직접 영향 없음
- Kafka가 lag를 버퍼링 → 일시적 ai-debate-svc 다운에도 메시지 유실 없음

### Negative / Trade-offs
- RN polling이 자원 낭비 — WebSocket push 개선 여지 (STEP 4 잔여)
- producer ↔ consumer ID 동기화 책임 발생 — `requestId` UUID 변환 실패 시 fallback 처리 필요

## Alternatives Considered

- **동기 REST + 90s timeout** — 사용자 경험·자원 점유 모두 열위. 기각.
- **WebSocket으로 RN이 ai-debate-svc 직접 호출** — service 경계 깨짐, market-svc 우회 → 도메인 책임 혼선. 기각.

## References

- 코드:
  - [DebateRequestedConsumer.kt:35-47](../ai-debate-svc/src/main/kotlin/com/invest/debate/infrastructure/kafka/DebateRequestedConsumer.kt)
  - [DebateController.kt:46-53](../ai-debate-svc/src/main/kotlin/com/invest/debate/infrastructure/web/DebateController.kt)
  - [docker-compose.yml:56-75](../docker-compose.yml) (kafka-init: 4개 토픽 명시 생성)
- 관련 ADR: [0008 (수동 ack)](0008-kafka-manual-ack-thread.md), [0005 (Redis TTL + Kafka 영속화)](0005-redis-ttl-kafka-cqrs.md)

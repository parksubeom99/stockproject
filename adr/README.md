# Architecture Decision Records (ADR)

stockProject의 핵심 설계 결정을 [Michael Nygard 표준 형식](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions)으로 기록한 문서들입니다.

각 ADR은 `Status / Context / Decision / Consequences (Positive·Negative) / Alternatives / References` 6단 구성이며, 모두 코드 SSOT(`file:line` 형식)에 근거합니다.

## 결정 목록

| # | 제목 | Status | 영역 |
|---|---|---|---|
| [0001](0001-spring-webflux.md) | Spring WebFlux 채택 (vs MVC) | Accepted | 기술 선택 |
| [0002](0002-kafka-async-decoupling.md) | Kafka 비동기 분리 (vs REST 동기) | Accepted | 인프라 |
| [0003](0003-ddd-hexagonal.md) | DDD + Hexagonal 아키텍처 | Accepted | 아키텍처 |
| [0004](0004-profile-based-adapter-swap.md) | 프로파일 기반 어댑터 스왑 (vs config flag) | Accepted | 아키텍처 |
| [0005](0005-redis-ttl-kafka-cqrs.md) | Redis 24h TTL + Kafka 이벤트 영속화 (가벼운 CQRS) | Accepted | 인프라 |
| [0006](0006-haiku-llm-model.md) | Claude Haiku 모델 선택 (vs Sonnet) | Accepted | 기술 선택 |
| [0007](0007-debatesession-private-constructor.md) | `DebateSession` private constructor + `create`/`reconstruct` 팩토리 | Accepted | 도메인 |
| [0008](0008-kafka-manual-ack-thread.md) | Kafka consumer 수동 ack + 별도 스레드 오케스트레이션 | Accepted | 인프라 |
| [0009](0009-karpathy-retry-saga.md) | KARPATHY 재시도 = Saga compensating transaction | Accepted | 도메인 |

## 의존 관계

- **ADR-0003 (DDD + Hexagonal)** 이 가장 기초. 0004·0007이 그 위에 쌓임.
- **ADR-0002 (Kafka 비동기 분리)** 가 0008·0005와 연결됨.
- **ADR-0001 (WebFlux)** 이 0008·0006의 reactive 전제 조건.

## 톤·형식 룰

- 3인칭 객관 톤 (1인칭 자기소개·면접 톤 금지)
- 한국어 본문 + 영어 용어 보존 (WebFlux, Kafka, ack, Saga 등)
- `Negative / Trade-offs` 절은 정직하게 작성 — 면접관·신규 엔지니어에게 limitation 투명 공개
- 새 ADR 추가 시 본 README 표 갱신

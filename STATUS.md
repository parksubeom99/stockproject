# stockProject 현황 점검 (Week 1 STEP 1.1)

> **작성 기준**: 워크트리 `lucid-burnell-48f33a` (HEAD = `f63fc98`, branch `claude/lucid-burnell-48f33a`)
> **작성일**: 2026-05-05
> **목적**: 4주 보강 진입 전 코드 정독 결과 및 인계서 대조 보고서. 코드 수정 없음.

---

## 1. 모듈 구성 (실제 측정)

| 모듈 | 언어/스택 | main LOC | test LOC | 비고 |
|---|---|---:|---:|---|
| `ai-debate-svc` | Kotlin 2.0 + Spring WebFlux + Reactor + Kafka | 1,262 | 267 | 5인 페르소나 오케스트레이션 |
| `market-svc` | Kotlin + WebFlux + Redis + WebSocket | 522 | 373 | 시세 캐시 + Kafka producer |
| `stock-app` | React Native + Expo (TS/TSX) | 1,184 | – | 모바일 클라이언트 |
| **합계** | | **2,968** (백엔드 1,784 + 앱 1,184) | **640** | |

**인계서 기재값(1,529 + 895)과의 차이**: 인계서는 `wc -l`이 아닌 다른 기준(주석 포함, 또는 IDE 측정)일 가능성. 실제 라인 수는 위 표가 정확.

---

## 2. 5인 페르소나 흐름 (검증)

`PhaseOrchestrator.kt` + `DebateOrchestrationService.kt` 직접 read 결과:

| Phase | Persona | 역할 | 출력 형식 |
|---|---|---|---|
| 1 | AMODEI (Dario Amodei) | 아키텍처 분석 + 리스크 3 + 반론 2 | `[분석] [리스크1/2/3] [반론1/2]` |
| 2 | ALTMAN (Sam Altman) | 현실성·전략성 평가 + 반론 2 | `[전략평가] [반론1/2]` |
| 3 | MUSK (Elon Musk) | 무자비한 반론 2+ + 대안 | `[반론1/2] [대안]` |
| 4 | KARPATHY (Andrej Karpathy) | PASS/HOLD/FAIL 판정 | `[판정] [근거] [반론]` |
| 5 | EL (총괄) | JSON 종합 리포트 | `{consensus, successProbability, disputes, actions[3]}` |

**Partial visibility 구조**: `session.buildContext()`로 이전 페르소나의 발언만 컨텍스트에 포함 → confirmation bias 차단 메커니즘.

**KARPATHY FAIL retry**: `DebateOrchestrationService.handleFail` → `session.handleKarpathyFail()` → `executeWithRetry` 재귀. `MAX_RETRY=2` (DebateSession 도메인 정의). 초과 시 `DebateFailedEvent` 발행.
재귀 최대 깊이 = MAX_RETRY + 1 = 3회 시도 보장 (초과 시 `DebateFailedEvent(reason="KARPATHY_MAX_RETRY")` 발행).

---

## 3. Kafka 토픽 매트릭스

| 토픽 | Producer | Consumer | 페이로드 |
|---|---|---|---|
| `debate.requested` | `market-svc.DebateKafkaProducer` | `ai-debate-svc.DebateRequestedConsumer` | `{userId, ticker, thesis, price}` |
| `debate.phase.completed` | `ai-debate-svc.KafkaEventPublisher` | (관측·로깅용) | `PhaseCompletedEvent` |
| `debate.completed` | `ai-debate-svc.KafkaEventPublisher` | (앱 polling 보조) | `{debateId, symbol, successProbability, status}` |
| `debate.failed` | `ai-debate-svc.KafkaEventPublisher` | (관측·재시도 트리거) | `{debateId, symbol, reason}` |

**docker-compose `kafka-init`이 4개 토픽 모두 명시 생성** (auto-create 의존 안 함) — 안정성 OK.

**`acks=all, retries=3`** (application.yml) — 메시지 유실 방어.

---

## 4. 프로파일 매트릭스 (Mock vs Prod)

| 모듈 | application.yml 기본값 | docker-compose 오버라이드 | mock 어댑터 | prod 어댑터 |
|---|---|---|---|---|
| `ai-debate-svc` | `mock` | `prod` | `MockLlmAdapter` | `ClaudeApiAdapter` |
| `market-svc` | `mock` | `mock` | `MockStockQuoteAdapter` | (Alpha Vantage, prod 시) |

**시연 환경 함의**:
- `docker compose up -d` → ai-debate는 **prod 모드**, 즉 `ANTHROPIC_API_KEY` 있어야 작동
- key 없으면 `ai-debate-svc` 부팅 실패 → Week 3 시연 환경에서 mock 강제 옵션 필요 (인계서 Week 3 작업 항목)

---

## 5. 포트 매트릭스

| 서비스 | 호스트 포트 | 컨테이너 포트 | 비고 |
|---|---:|---:|---|
| ai-debate-svc | 8083 | 8083 | 동일 매핑 |
| market-svc | 8085 | 8081 | **호스트 8085, 컨테이너 8081 (불일치 매핑)** |
| kafka | 9093 | 9092 | 호스트 9093 외부용, 9092 내부용 |
| redis | 6380 | 6379 | 표준 6379 충돌 회피 |
| zookeeper | 2182 | 2181 | 표준 2181 충돌 회피 |

**ai-debate의 redis 설정**: `application.yml`이 `${REDIS_PORT:6380}`을 기본값으로 사용 — 로컬 직접 실행 시 호스트 매핑(6380)을 보고 있음. 컨테이너 환경에선 `REDIS_PORT=6379`로 오버라이드되어 정합성 OK.

---

## 6. REST API (DebateController.kt 검증)

| 메서드 | 경로 | 용도 | 응답 |
|---|---|---|---|
| POST | `/debate/start` | 토론 시작 | 202 Accepted + `{debateId}` |
| GET | `/debate/{id}/report` | 완료 레포트 조회 | `DebateReportResponse` |
| GET | `/debate/{id}/status` | 진행 상태 조회 (앱 polling용) | `IN_PROGRESS` / `COMPLETED` (도메인 `DebateStatus` enum 4종: `REQUESTED, IN_PROGRESS, COMPLETED, FAILED` 중 endpoint는 2종만 노출) |

**README 정합성**: 라인 181 = `/debate/start` ✅ (a7b06c2 이전 커밋에서 정정 완료).

---

## 7. Hexagonal 구조 (검증)

```
ai-debate-svc/src/main/kotlin/com/invest/debate/
├── domain/        — model, port, event (의존성 0)
├── application/   — service (PhaseOrchestrator, DebateOrchestrationService)
└── infrastructure/
    ├── llm/       — ClaudeApiAdapter, MockLlmAdapter
    ├── kafka/     — KafkaEventPublisher, DebateRequestedConsumer, KafkaConfig
    ├── persistence/ — Redis 영속성
    └── web/       — DebateController
```

Port 분리 (도메인 → 인프라 의존성 역전):
- `LlmPort` ← `ClaudeApiAdapter` / `MockLlmAdapter` (`@Profile`)
- `EventPublisherPort` ← `KafkaEventPublisher`
- `DebateSessionRepository` ← `RedisDebateSessionRepository`
- `StartDebateUseCase`, `GetDebateReportUseCase`, `RetryDebateUseCase` ← `DebateOrchestrationService`

---

## 8. 테스트 매트릭스 (실제 카운트)

| 파일 | @Test 메서드 |
|---|---:|
| `ai-debate-svc/.../PhaseOrchestratorTest.kt` | 5 |
| `ai-debate-svc/.../DebateSessionTest.kt` | 8 |
| `market-svc/.../DebateKafkaProducerTest.kt` | 2 |
| `market-svc/.../MarketServiceTest.kt` | 6 |
| `market-svc/.../MockStockQuoteAdapterTest.kt` | 3 |
| `market-svc/.../QuoteWebSocketHandlerTest.kt` | 2 |
| `market-svc/.../WebSocketHandshakeInterceptorTest.kt` | 5 |
| **합계** | **31** |

인계서의 "31 tests PASS"는 위 7개 파일 × 평균 4.4개 메서드의 합산. 실제 PASS 여부는 회장님 로컬에서 `./gradlew test` 검증 필요 (이 채팅은 ZIP/워크트리 정독만 가능).

**Claude API 모델**: `claude-haiku-4-5-20251001` (application.yml 라인 32). 주석 명시 — "Sonnet → Haiku: 5~10배 빠름, 1/10 비용".

---

## 9. 인계서 대조 — Week 1 잔여 작업

| Week 1 STEP | 인계서 추정 | 실제 상태 | 결론 |
|---|---|---|---|
| 1.1 코드 정독 + STATUS.md | 4시간 | 본 문서 | ✅ 본 STEP에서 완료 |
| 1.2 Mock/Prod 프로파일 검증 | 2시간 | 코드 정합성만 OK, 실부팅 미검증 | ⏳ 회장님 로컬 (`docker compose up`) |
| 1.3 Kafka·Redis 의존 점검 | 2시간 | docker-compose healthcheck·depends_on 정합성 OK | ⏳ 회장님 로컬 |
| 1.4-A README P0 정정 (라인 181 `/debate/start`) | – | a7b06c2 (포트·API path 정합성) | ✅ 처리 완료 |
| 1.4-B README 한국어 1차 보강 (전체 톤 다듬기) | 4시간 | 미수행 | ⏳ 미완 |

**남은 Week 1 = 1.2 + 1.3 + 1.4-B** (1.2/1.3은 회장님 로컬 docker 부팅 + ANTHROPIC_API_KEY 검증, 1.4-B는 README 한국어 톤 1차 보강).

---

## 10. Week 2~4 진입 전 체크리스트 (참고용)

- [ ] Week 2 ADR 3종 작성 위치: 워크트리 루트 `adr/` 디렉터리 신설 필요 (현재 부재)
- [ ] Week 3 k6 시나리오 위치: 워크트리 루트 `perf/` 디렉터리 신설 필요 (현재 부재)
- [ ] Week 3 시연 환경 — `ANTHROPIC_API_KEY` 없이 부팅 가능한 mock 프로파일 docker-compose 별도 작성 (현재 prod 강제)
- [ ] Week 4 시연 영상 — 5분 시한 / GitHub repo는 이미 PUBLIC (`gh repo view` 검증, 실행일 2026-05-10)

---

## 변경 이력

- v0.1 · 2026-05-05 · 워크트리 `lucid-burnell-48f33a` 직접 read 기반 작성

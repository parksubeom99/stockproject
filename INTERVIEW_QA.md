# INTERVIEW_QA.md — stockProject 면접 Q&A 12선

> Week 1 STEP 8 산출물. 면접관이 stockProject 설계·구현에 대해 던질 가능성이 높은 질문 12개와 1인칭 답변을 정리.
> 모든 답변은 코드 SSOT(`file:line` 형식 인용)에 근거. README·ARCHITECTURE·STATUS 톤-β 유지 — 한국어 본문 + 영어 용어 보존(WebFlux, Kafka, partial visibility, ack 등).

**목차**
- §1 도메인 설계 (Q1~Q4) — 5인 페르소나·partial visibility·KARPATHY 재시도·EL strict JSON
- §2 아키텍처 (Q5~Q7) — DDD/Hex·프로파일 스왑·`DebateSession` 캡슐화
- §3 인프라 (Q8~Q10) — Kafka 선택·Redis 영속성·수동 ack 패턴
- §4 기술 선택 (Q11~Q12) — WebFlux·Haiku

---

## §1 도메인 설계

### Q1. 5인 페르소나(AMODEI → ALTMAN → MUSK → KARPATHY → EL)는 왜 이 구성·이 순서인가요?

**A.** AI 투자 분석에서 가장 큰 리스크는 **단일 모델이 자기 의견을 confirmation bias로 강화하는 것**이라고 봤습니다. 그래서 한 모델이 5개 역할을 페르소나로 분리해서 **순차적으로 서로 반박하게** 했습니다.

순서는 의도된 분업입니다 — AMODEI(아키텍트, 리스크 3개+반론 2개) → ALTMAN(현실성 전략 검토) → MUSK(first principles 무자비한 반박) → KARPATHY(QA 게이트, `PASS`/`HOLD`/`FAIL` 판정) → EL(종합·합의·액션 3개). 코드상 `PersonaType` enum에 `phaseNum`이 박혀 있고([Enums.kt:10-16](ai-debate-svc/src/main/kotlin/com/invest/debate/domain/model/Enums.kt#L10-L16)), `PhaseOrchestrator.runPhases`가 이 순서를 강제합니다([PhaseOrchestrator.kt:65-76](ai-debate-svc/src/main/kotlin/com/invest/debate/application/service/PhaseOrchestrator.kt#L65-L76)).

**Follow-up 힌트:** 만약 면접관이 "5명이 너무 많지 않냐"고 물으면 — KARPATHY를 빼면 자기 검증 단계가 없고, EL을 빼면 합의·액션이 안 나옵니다. 분석(AMODEI)→ 전략(ALTMAN)→ 반박(MUSK)이 thesis를 3축으로 검증하는 최소 단위입니다.

---

### Q2. "partial visibility" 가 정확히 어떤 의미인가요? 왜 그게 설계의 핵심인가요?

**A.** **각 페르소나는 자기 차례 직전까지의 발언만 본다**는 규칙입니다. 코드상으로는 `runPhases`가 `phases.fold`로 누적해서 다음 페르소나 호출 시 `session.buildContext()`를 user prompt에 주입하고([PhaseOrchestrator.kt:73-89](ai-debate-svc/src/main/kotlin/com/invest/debate/application/service/PhaseOrchestrator.kt#L73-L89)), `buildContext()`는 `phases`에 들어간 결과만 joinToString으로 합칩니다([DebateSession.kt:92-99](ai-debate-svc/src/main/kotlin/com/invest/debate/domain/model/DebateSession.kt#L92-L99)).

이게 핵심인 이유는 **5명에게 동시에 전체 thesis를 던지면 결국 5개 답변이 비슷해지기 때문**입니다. 다음 페르소나가 직전 페르소나의 결론을 보고 거기에 반박하도록 강제해야 진짜 토론이 됩니다. CLAUDE.md에도 "partial-visibility rule is the whole point of the design; don't broaden it"으로 못 박아 뒀습니다.

**Follow-up 힌트:** 만약 "Map-Reduce 패턴이랑 뭐가 다르냐"고 물으면 — Map-Reduce는 병렬+독립이지만 이건 순차+의존입니다. blackboard pattern에 더 가깝되 전체 blackboard가 아닌 prefix만 공개합니다.

---

### Q3. KARPATHY가 `FAIL` 판정하면 `MAX_RETRY = 2`까지 재시도하는데, 이건 어떤 패턴인가요?

**A.** **Saga 패턴의 compensating transaction** 입니다. KARPATHY가 fail이면 `DebateSession.handleKarpathyFail()`이 `retryCount++` 한 뒤 **Phase 3(MUSK) 이상을 phases에서 제거**해서 MUSK부터 다시 실행합니다([DebateSession.kt:69-78](ai-debate-svc/src/main/kotlin/com/invest/debate/domain/model/DebateSession.kt#L69-L78)). 즉 AMODEI·ALTMAN 분석은 신뢰하고 보존하되, MUSK 반박 + KARPATHY 검증만 재실행합니다.

`MAX_RETRY = 2`로 제한한 이유는 무한 루프 방지 + 비용 통제입니다(Claude API 호출 1회 ≈ 800 토큰). 한도 초과 시 `status = FAILED`로 전이하고 `DebateFailedEvent`를 Kafka에 publish합니다([DebateOrchestrationService.kt:116-133](ai-debate-svc/src/main/kotlin/com/invest/debate/application/service/DebateOrchestrationService.kt#L116-L133)).

**Follow-up 힌트:** "왜 처음부터 다시 안 돌리냐"고 물으면 — AMODEI·ALTMAN은 thesis 자체를 분석하는 단계라 재실행해도 답이 거의 같습니다. 재실행할 가치는 MUSK 반박부터입니다.

---

### Q4. EL이 strict JSON을 어기면 어떻게 처리되나요? `actions=3` 불변식은요?

**A.** 2단 방어입니다. 1단 — `parseElOutput`이 LLM 응답에서 **중괄호 블록만 정규식으로 추출**합니다(`indexOf('{')` ... `lastIndexOf('}')`). LLM이 JSON 앞뒤에 자연어 붙이는 경우를 흡수합니다([PhaseOrchestrator.kt:165-198](ai-debate-svc/src/main/kotlin/com/invest/debate/application/service/PhaseOrchestrator.kt#L165-L198)). 2단 — 파싱 실패 시 `successProbability = 50` 카더라 fallback report를 반환합니다.

`actions=3` 불변식은 두 군데서 강제됩니다. 파서 단계에서 actions가 부족하면 패딩, 넘치면 take(3)([PhaseOrchestrator.kt:183-188](ai-debate-svc/src/main/kotlin/com/invest/debate/application/service/PhaseOrchestrator.kt#L183-L188)). 그리고 `DebateReport` value object의 init 블록에서 `require(actions.size == 3)` 으로 도메인 진입 시 한 번 더 강제([ValueObjects.kt:36-40](ai-debate-svc/src/main/kotlin/com/invest/debate/domain/model/ValueObjects.kt#L36-L40)). 어떤 경로로 만들어지든 invariant 위반은 불가능합니다.

**Follow-up 힌트:** "왜 정확히 3개냐"고 물으면 — RN UI 카드 슬롯이 3개로 고정. 도메인 invariant가 UI 제약을 흡수한 케이스입니다(현실적 절충, 깔끔한 정답은 UI를 가변으로).

---

## §2 아키텍처

### Q5. DDD + Hexagonal 을 택한 이유는요?

**A.** ai-debate-svc는 **외부 의존(Claude API, Redis, Kafka)이 많은데 단위 테스트는 빨라야 하는** 상황이라 hexagonal이 잘 맞았습니다. 도메인 코어(`PhaseOrchestrator`, `DebateOrchestrationService`)는 `LlmPort` / `DebateSessionRepository` / `EventPublisherPort` 인터페이스에만 의존하고([Ports.kt:31-54](ai-debate-svc/src/main/kotlin/com/invest/debate/domain/port/Ports.kt#L31-L54)), 어댑터는 `infrastructure/` 패키지에서 프로파일별로 갈아 끼웁니다.

DDD는 `DebateSession` 애그리거트가 invariant(상태 전이, retry count, phases 추가)를 자기 안에 가두기 위해 적용했습니다([DebateSession.kt:55-90](ai-debate-svc/src/main/kotlin/com/invest/debate/domain/model/DebateSession.kt#L55-L90)). `require(status == DebateStatus.REQUESTED)` 같은 가드가 도메인 안에 있어서 application 레이어가 상태 전이 순서를 잘못 호출하면 즉시 실패합니다.

**Follow-up 힌트:** "오버엔지니어링 아니냐"고 물으면 — market-svc는 의도적으로 hexagonal을 얕게 적용했습니다. 도메인 규칙이 단순한(quote 스트리밍) 서비스까지 DDD를 강요하지 않았다는 게 답입니다.

---

### Q6. 어댑터를 Spring 프로파일(`@Profile("prod")` / `mock`)로 스왑하는데, 왜 config flag가 아니라 프로파일인가요?

**A.** **Mock과 Prod는 다른 빈(bean) 자체를 등록해야 하기 때문**입니다. 예를 들어 `ClaudeApiAdapter`는 `@Profile("prod")`로([ClaudeApiAdapter.kt:15](ai-debate-svc/src/main/kotlin/com/invest/debate/infrastructure/llm/ClaudeApiAdapter.kt#L15)) 실 API 호출 + `ANTHROPIC_API_KEY` env가 필수인데, mock 환경에서 이 빈을 로드하면 키 없어서 부팅이 실패합니다. 프로파일은 빈 생성 자체를 막아주지만 config flag는 빈 생성 후 분기라 부팅 시점 검증이 안 됩니다.

`DebateSessionRepository`도 같은 패턴 — `RedisDebateSessionRepository`는 `@Profile("prod")`([RedisDebateSessionRepository.kt:23](ai-debate-svc/src/main/kotlin/com/invest/debate/infrastructure/persistence/RedisDebateSessionRepository.kt#L23)), `InMemoryDebateSessionRepository`는 `@Profile("!prod")`로 갈라 Redis 없이도 단위 테스트가 standalone으로 돕니다.

**Follow-up 힌트:** "테스트 컨테이너 쓰지 그러냐"고 물으면 — 통합 테스트는 testcontainers를 검토 중이지만(STEP 4 잔여), 단위 테스트 속도가 우선이라 in-memory를 디폴트로 둡니다.

---

### Q7. `DebateSession`이 private constructor + `create()`/`reconstruct()` 팩토리로 갈라져 있는 이유는요?

**A.** **Redis snapshot round-trip 때문**입니다. `create()`는 새 세션을 만들 때 invariant(status=REQUESTED, retryCount=0, createdAt=now)를 강제하지만, Redis에서 JSON으로 직렬화된 세션을 역직렬화할 때는 이미 IN_PROGRESS / phases가 차 있을 수도 있습니다. `reconstruct()`는 그 모든 상태를 받아 그대로 복원합니다([DebateSession.kt:22-52](ai-debate-svc/src/main/kotlin/com/invest/debate/domain/model/DebateSession.kt#L22-L52)).

만약 public constructor를 열어두면 application 코드가 실수로 세션을 IN_PROGRESS 상태로 직접 만들어버릴 수 있습니다. private constructor + 2개 팩토리는 **"새 세션은 create로만, 복원은 reconstruct로만"** 강제하는 컴파일 타임 가드입니다. Jackson은 `DebateSessionSnapshot` DTO를 거쳐 `toDomain()`에서 reconstruct를 호출합니다([RedisDebateSessionRepository.kt:74-96](ai-debate-svc/src/main/kotlin/com/invest/debate/infrastructure/persistence/RedisDebateSessionRepository.kt#L74-L96)).

**Follow-up 힌트:** "JPA 엔티티는 이런 거 안 하는데"라고 물으면 — JPA는 ORM이 reflection으로 빈 생성자 호출이 필요하지만, 우리는 Redis JSON snapshot이라 ORM 제약이 없습니다. 그래서 더 엄격한 캡슐화가 가능했습니다.

---

## §3 인프라

### Q8. RN → ai-debate-svc 를 REST 한 번에 부르지 않고 Kafka(`debate.requested`)로 갈라놓은 이유는요?

**A.** **응답 지연(10~25초)을 producer 쪽에서 절단하기 위해서** 입니다. RN이 `POST /debate/request`를 호출하면 market-svc는 즉시 Kafka에 publish하고 `requestId`만 반환합니다 — 그 동안 ai-debate-svc는 Kafka consumer가 비동기로 받아서 처리합니다. RN은 `GET /debate/{id}/status`를 3초 주기로 polling해서 `COMPLETED` 뜨면 report 조회합니다([DebateController.kt:46-53](ai-debate-svc/src/main/kotlin/com/invest/debate/infrastructure/web/DebateController.kt#L46-L53)).

부수 효과로 **service 간 backpressure 분리** 가 생깁니다 — Claude API rate limit에 걸려도 market-svc는 영향 없고, Kafka가 lag를 버퍼링합니다. 그리고 polling용 `debateId`는 producer(market-svc)의 `requestId`(UUID)를 consumer가 그대로 `externalId`로 받아 `DebateSession.create(externalId=...)`에 주입해서 정합성을 맞춥니다([DebateRequestedConsumer.kt:35-47](ai-debate-svc/src/main/kotlin/com/invest/debate/infrastructure/kafka/DebateRequestedConsumer.kt#L35-L47)).

**Follow-up 힌트:** "WebSocket으로 push 하면 polling 안 해도 되잖아"라고 물으면 — 맞습니다. STEP 4 잔여 항목. WebSocket으로 `debate.completed` 알림을 RN에 흘리는 게 다음 단계입니다.

---

### Q9. Redis에 세션을 24h TTL로 두는데, 이게 적절한 영속성인가요? 영구 저장 안 해도 되나요?

**A.** **토론 세션은 일회성 산출물** 이라는 도메인 판단입니다. 사용자가 "엔비디아 thesis 검증해줘" 라고 던지면 그 결과는 그 자리에서 보고 의사결정에 쓰는 거지, 1년 뒤 다시 조회할 일이 거의 없습니다. 그래서 Redis `Duration.ofHours(24)`로 자동 만료시킵니다([RedisDebateSessionRepository.kt:33](ai-debate-svc/src/main/kotlin/com/invest/debate/infrastructure/persistence/RedisDebateSessionRepository.kt#L33)).

대신 **장기 보관이 필요한 시그널만 Kafka 이벤트로 흘립니다** — `DebateCompletedEvent`(successProbability, status)와 `DebateFailedEvent`(reason)는 별도 토픽으로 발행돼서 다운스트림(통계, 백테스트, BI)이 자기 store에 쌓을 수 있게 했습니다([DebateOrchestrationService.kt:104-132](ai-debate-svc/src/main/kotlin/com/invest/debate/application/service/DebateOrchestrationService.kt#L104-L132)). 세션 raw는 휘발, 시그널은 영속 — CQRS의 가벼운 적용입니다.

**Follow-up 힌트:** "Redis 24h TTL 안에 RN이 못 보면 어쩌냐"고 물으면 — 그 케이스를 대비해 status polling이 NoSuchElement 시에도 `IN_PROGRESS`로 정상 응답하게 했습니다([DebateController.kt:52](ai-debate-svc/src/main/kotlin/com/invest/debate/infrastructure/web/DebateController.kt#L52)) — 단 만료 후엔 영원히 IN_PROGRESS로 보이는 한계가 있고, STEP 5 RUNBOOK 항목입니다.

---

### Q10. Kafka consumer가 `enable-auto-commit: false` + 수동 `ack.acknowledge()` 후 `Thread { orchestrate(...).block() }.start()` 로 별도 스레드 실행하는데, 이 패턴 의도는요?

**A.** **Consumer poll 루프를 블로킹하지 않으려는 의도** 입니다. 오케스트레이션 전체는 Claude API 5번 호출 = 50~125초 걸리는 long-running 작업입니다. 그걸 consumer 콜백 안에서 block하면 poll이 멈춰서 Kafka가 consumer를 죽은 걸로 판단(`session.timeout.ms` 초과)하고 rebalance를 트리거합니다.

그래서 패턴은 **(1) 세션 생성 + DB 저장까지만 sync로 하고 → (2) ack.acknowledge() 로 offset 커밋 → (3) 오케스트레이션은 별도 Thread에 떼서 실행** 입니다([DebateRequestedConsumer.kt:39-59](ai-debate-svc/src/main/kotlin/com/invest/debate/infrastructure/kafka/DebateRequestedConsumer.kt#L39-L59)). `auto-commit=false`로 둔 이유는 "세션 생성이 실패하면 ack 안 하고 메시지를 재처리" 하기 위해서입니다. CLAUDE.md에도 "preserve `ack.acknowledge()` ordering when editing `DebateRequestedConsumer`" 라고 못 박았습니다.

**Follow-up 힌트:** "raw Thread는 안티패턴 아니냐"고 물으면 — 맞습니다. 진짜 production은 `@Async` + `TaskExecutor` 또는 Coroutine scope입니다. 현재 단순 PoC라 raw Thread로 두고 STEP 5 RUNBOOK에 "스레드 풀 도입" 으로 적어뒀습니다.

---

## §4 기술 선택

### Q11. Spring MVC가 더 흔한데 굳이 WebFlux를 쓴 이유는요?

**A.** 두 가지입니다. (1) **WebSocket quote 스트리밍이 reactive native** 입니다 — market-svc가 3초 주기로 `Flux<StockQuote>`를 `distinctUntilChanged`로 흘리는데([MockStockQuoteAdapter](market-svc/src/main/kotlin/com/stockproject/market/adapter/external/MockStockQuoteAdapter.kt) 패턴), MVC 스레드 모델에선 connection당 스레드라 RN 클라이언트 100개 붙으면 톰캣 스레드가 100개 점유됩니다. WebFlux는 Netty event-loop이라 같은 스레드로 수천 connection 가능.

(2) **Claude API 호출이 IO-bound + 90초 long-tail** 입니다([ClaudeApiAdapter.kt:54](ai-debate-svc/src/main/kotlin/com/invest/debate/infrastructure/llm/ClaudeApiAdapter.kt#L54)). MVC + RestTemplate은 호출 동안 스레드 잡고 있지만, WebFlux + WebClient는 callback으로 흘려서 idle합니다. `PhaseOrchestrator.runPhases`도 `Mono<DebateSession>` 으로 5단 chain([PhaseOrchestrator.kt:65-76](ai-debate-svc/src/main/kotlin/com/invest/debate/application/service/PhaseOrchestrator.kt#L65-L76)) — flatMap 조합이 reactive에 자연스럽습니다.

**Follow-up 힌트:** "reactive 디버깅 어렵지 않냐"고 물으면 — 네, stack trace가 끊깁니다. 그래서 `doOnSuccess` / `doOnError`로 의도적으로 로깅 포인트를 박았습니다([ClaudeApiAdapter.kt:59-60](ai-debate-svc/src/main/kotlin/com/invest/debate/infrastructure/llm/ClaudeApiAdapter.kt#L59-L60)).

---

### Q12. Claude Sonnet 대신 Haiku를 택한 이유는요? 성능 손실은 없나요?

**A.** **Haiku는 Sonnet 대비 5~10배 빠르고 1/10 비용** 입니다(application.yml에도 주석으로 명시 — [application.yml:32](ai-debate-svc/src/main/resources/application.yml#L32)). 5인 페르소나가 순차 호출이라 latency가 곱셈으로 누적되는데, Sonnet으로 페르소나당 60~120초면 전체 5~10분 → 사용자 이탈. Haiku로 10~25초/페르소나면 전체 50~125초 → polling으로 견딜 만한 범위.

성능 손실은 prompt engineering으로 흡수했습니다 — system prompt에서 페르소나 역할·출력 형식·"한국어로 응답"을 강하게 명시하고([ClaudeApiAdapter.kt:64-81](ai-debate-svc/src/main/kotlin/com/invest/debate/infrastructure/llm/ClaudeApiAdapter.kt#L64-L81)), `maxTokens = 800`으로 응답 길이를 절단해서 비용·속도를 추가로 절약합니다([ClaudeApiAdapter.kt:40](ai-debate-svc/src/main/kotlin/com/invest/debate/infrastructure/llm/ClaudeApiAdapter.kt#L40)).

**Follow-up 힌트:** "그래도 추론 깊이는 Sonnet이 낫지 않냐"고 물으면 — 동의합니다. KARPATHY(QA 검증)만 Sonnet으로 올리는 hybrid 구성을 검토 중입니다 — 판정 정확도가 전체 retry 비용을 좌우하니까 비용 대비 효과가 가장 큽니다.

---

## 부록 — 자주 받을 메타 질문

- **"이 프로젝트 혼자 만든 거 맞나요?"** — 네. 5인 페르소나·partial visibility 설계, Kotlin/WebFlux/Kafka/Redis 인프라, RN 클라이언트까지 단독 작업입니다. Claude Code를 페어 프로그래밍 도구로 활용했고(코드 작성·리뷰), 설계 결정과 도메인 invariant는 직접 정의했습니다.
- **"가장 어려웠던 부분은요?"** — `DebateSession`이 mutable 상태(status, retryCount, phases)를 갖는데 Redis snapshot으로 round-trip 시켜야 했던 부분. private constructor + `create`/`reconstruct` 분리로 풀었습니다(Q7 참조).
- **"다음에 고치고 싶은 부분은요?"** — raw `Thread`를 `@Async + TaskExecutor`로(Q10), RN polling을 WebSocket push로(Q8), 통합 테스트를 testcontainers로(Q6). STEP 4·5에 잔여로 명시.

---

_본 문서는 코드 SSOT(`ai-debate-svc/src/main/kotlin/**`) 직접 read 후 작성. README·ARCHITECTURE와 사실 정합 검증 완료. 면접 직전 한 번 더 코드 변경 여부 확인 권장._

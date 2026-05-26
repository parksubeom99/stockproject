# RUNBOOK — 장애 대응 시나리오

> StockProject 운영자/온콜용 장애 대응 절차. **5개 장애 시나리오**의 증상·원인·코드 처리 위치·즉시 대응·사후 분석·재발 방지를 정리.
>
> 원본 SSOT: [ARCHITECTURE.md §7.3 에러 모드 분석](../ARCHITECTURE.md). 본 문서는 표 1행을 본문으로 풀어 쓴 것. 시나리오 추가·수정 시 양쪽 동기화 의무.

---

## 0. 운영 전제

### 0.1 환경
- 시연/프로덕션 환경: **docker-compose** (root [docker-compose.yml](../docker-compose.yml))
- 핵심 컨테이너: `stock-zookeeper`, `stock-kafka`, `stock-redis`, `stock-debate-svc`, `stock-market-svc`
- 외부 의존: **Claude API** (`api.anthropic.com`, `ANTHROPIC_API_KEY` 필요)

### 0.2 로그 위치
| 서비스 | 로그 명령 |
|---|---|
| ai-debate-svc | `docker logs stock-debate-svc --tail 200` |
| market-svc | `docker logs stock-market-svc --tail 200` |
| Kafka | `docker logs stock-kafka --tail 100` |
| Redis | `docker logs stock-redis --tail 50` |

로그 라벨 컨벤션:
- `[Debate:{uuid}]` — 토론 세션 단위 추적. 이 prefix로 grep 시 한 토론의 전체 라이프사이클 추출 가능.
- `[LLM]` — Claude API 호출/응답.
- `[Kafka]` — Consumer 수신/Producer 발행.
- `[Redis SAVE]` — 세션 저장 (DEBUG 레벨).

### 0.3 접근 권한
- 운영 환경 SSH/Docker 접근: 단독 작업 (박수범)
- Claude API 키 회수/재발급: [Anthropic Console](https://console.anthropic.com)

---

## S1. Claude API timeout / 5xx

### 증상 (관측)
- 로그 패턴:
  ```
  [LLM] {페르소나} 호출 실패: Read timeout / 5xx / Connection refused
  [Debate:{uuid}] 오류 발생: ...
  ```
- 사용자 영향: RN polling 결과가 `IN_PROGRESS` → `FAILED`로 전환. `GET /debate/{id}/report` 호출 시 `NoSuchElementException` 발생.
- 메트릭: `[LLM] {페르소나} 응답 완료` 라인이 90초 이내 나타나지 않음.

### 원인 (Root Cause)
- Claude API 일시 장애 (5xx)
- 네트워크 타임아웃 (Haiku 평균 10~25s, 90s 상한 도달은 비정상)
- API 키 만료/한도 초과 (401/429)

### 코드 처리 위치
- [ClaudeApiAdapter.kt:54](../ai-debate-svc/src/main/kotlin/com/invest/debate/infrastructure/llm/ClaudeApiAdapter.kt) — `.timeout(Duration.ofSeconds(90))` 90초 상한
- [ClaudeApiAdapter.kt:61](../ai-debate-svc/src/main/kotlin/com/invest/debate/infrastructure/llm/ClaudeApiAdapter.kt) — `.retry(1)` 네트워크 오류 시 1회 재시도
- [DebateOrchestrationService.kt:80](../ai-debate-svc/src/main/kotlin/com/invest/debate/application/service/DebateOrchestrationService.kt) — `.onErrorResume` → `session.fail()` → 세션 FAILED 저장
- 90초 + 1회 재시도 = 최대 약 180초 대기 후 FAILED.

### 즉시 대응 (운영자 첫 5분)
1. **API 상태 확인**: https://status.anthropic.com 페이지 열기
2. **로그 패턴 분류**:
   ```bash
   docker logs stock-debate-svc --tail 200 | grep "\[LLM\]" | tail -20
   ```
   - `Read timeout` → 네트워크 또는 API 지연
   - `401 Unauthorized` → `ANTHROPIC_API_KEY` 만료/오타
   - `429 Too Many Requests` → 한도 초과
3. **API 키 검증** (401인 경우):
   ```bash
   docker exec stock-debate-svc env | grep ANTHROPIC_API_KEY
   ```
4. **재시도 가능 여부 회장님 판단**: RN에서 동일 thesis로 새 토론 요청 (UUID 새로 발급).

### 사후 분석 (로그/메트릭 확인 절차)
- 실패한 debateId 추출:
  ```bash
  docker logs stock-debate-svc | grep "오류 발생" | tail -10
  ```
- 시계열 패턴: 특정 시간대 집중 vs 산발. 집중이면 외부 장애, 산발이면 네트워크 흔들림 가능성.
- API 키 사용량: Anthropic Console → Usage 탭에서 한도 도달 여부.

### 재발 방지 (장기 개선)
- ARCHITECTURE.md §10 잔여로 등록되어 있음: **k6 부하 시나리오 (STEP 4)** 에서 timeout 임계값 검증.
- Circuit breaker (Resilience4j) 도입 검토 — 연속 N회 실패 시 일정 시간 신규 호출 차단.
- 5xx vs network timeout vs auth 오류 분리 로깅 추가 (현재는 `e.message` 단일 라인).

---

## S2. Redis 연결 끊김 (save/findById 실패)

### 증상 (관측)
- 로그 패턴:
  ```
  [Debate:{uuid}] 오류 발생: Connection refused / Read timed out
  RedisCommandTimeoutException / RedisConnectionException
  ```
- 사용자 영향: 신규 토론 요청 시 `startDebate` 단계에서 즉시 실패. polling 시 sesssion 자체 조회 불가.
- 헬스체크: `docker ps --filter name=stock-redis` 가 `Up` 이 아니거나 unhealthy.

### 원인 (Root Cause)
- Redis 컨테이너 OOM/크래시
- 네트워크 단절 (Docker network 이슈)
- `REDIS_HOST`/`REDIS_PORT` 환경변수 오기

### 코드 처리 위치
- [application.yml:14](../ai-debate-svc/src/main/resources/application.yml) — `timeout: 3000ms` Redis 명령 타임아웃
- [RedisDebateSessionRepository.kt:37](../ai-debate-svc/src/main/kotlin/com/invest/debate/infrastructure/persistence/RedisDebateSessionRepository.kt) — `save` 메서드는 onError 처리 없이 그대로 전파
- [DebateOrchestrationService.kt:80](../ai-debate-svc/src/main/kotlin/com/invest/debate/application/service/DebateOrchestrationService.kt) — orchestration 외곽의 `.onErrorResume` 에서 잡힘 → `session.fail()`
- market-svc 의 quote 캐시는 별도 처리: [RedisQuoteCacheAdapter.kt:31](../market-svc/src/main/kotlin/com/stockproject/market/infrastructure/redis/RedisQuoteCacheAdapter.kt) — `.onErrorResume { Mono.empty() }` (캐시 미스로 디그레이드, 토론 흐름 영향 없음)

### 즉시 대응 (운영자 첫 5분)
1. **컨테이너 상태 확인**:
   ```bash
   docker ps -a --filter name=stock-redis
   docker logs stock-redis --tail 50
   ```
2. **재시작 시도**:
   ```bash
   docker compose restart redis
   ```
3. **ai-debate-svc 재기동** (Redis 연결 복구 후 자동 재연결되지만 명시적으로):
   ```bash
   docker compose restart ai-debate-svc
   ```
4. **데이터 손실 인지**: TTL 24h로 in-memory 보관이라 컨테이너 크래시 시 진행 중 토론 세션은 모두 유실. 사용자에게 재요청 안내.

### 사후 분석 (로그/메트릭 확인 절차)
- Redis 메모리 사용량:
  ```bash
  docker exec stock-redis redis-cli -p 6379 INFO memory | grep used_memory_human
  ```
- 키 개수:
  ```bash
  docker exec stock-redis redis-cli -p 6379 DBSIZE
  ```
- 크래시 직전 마지막 명령 패턴: Redis slowlog
  ```bash
  docker exec stock-redis redis-cli -p 6379 SLOWLOG GET 10
  ```

### 재발 방지 (장기 개선)
- ARCHITECTURE.md §10 잔여: **Redis 장애 시 그레이스풀 디그레이드 실증 (Week 1 STEP 5)** — 본 문서가 1차 대응 절차. 실측은 다음 sprint.
- Redis 메모리 상한 설정 (`maxmemory` + `maxmemory-policy allkeys-lru`)
- Redis Sentinel/Cluster 도입은 본 프로젝트 범위 외 (단일 노드 시연 환경).

---

## S3. Kafka publish 실패

### 증상 (관측)
- 로그 패턴:
  ```
  KafkaProducerException / TimeoutException / Failed to send producer request
  ```
- 사용자 영향: **토론 자체는 완료되지만 외부 알람/분석 시스템 미통지**. RN polling은 정상 작동 (polling은 Kafka 비의존).
- 메트릭: `[Debate:{uuid}] 완료 처리 | 성공확률=N%` 로그는 나오지만 후속 이벤트 발행 로그 없음.

### 원인 (Root Cause)
- Kafka broker 다운
- Topic 미존재 (kafka-init 컨테이너 실행 실패)
- 네트워크 분리 (`KAFKA_BOOTSTRAP_SERVERS` 미해결)

### 코드 처리 위치
- [application.yml:27-28](../ai-debate-svc/src/main/resources/application.yml) — Producer 설정
  ```yaml
  acks: all       # 모든 ISR replica ack 대기 (단일 노드에선 사실상 leader만)
  retries: 3      # 일시 실패 시 자체 재시도
  ```
- 같은 파일 line 23: `enable-auto-commit: false` — Consumer는 manual ack
- [DebateOrchestrationService.kt:112](../ai-debate-svc/src/main/kotlin/com/invest/debate/application/service/DebateOrchestrationService.kt) — `eventPublisher.publish(event)` 실패 시에도 토론 완료 자체는 보존 (handleSuccess의 then.flatMap 구조)
- **설계 의도**: 이벤트 발행 실패는 토론 비즈니스 흐름을 무효화하지 않음. 외부 관측만 누락.

### 즉시 대응 (운영자 첫 5분)
1. **Kafka 상태**:
   ```bash
   docker ps --filter name=stock-kafka
   docker logs stock-kafka --tail 100 | grep -i "error\|fatal"
   ```
2. **Topic 존재 확인**:
   ```bash
   docker exec stock-kafka kafka-topics --bootstrap-server localhost:9092 --list
   ```
   기대값: `debate.requested`, `debate.completed`, `debate.failed`, `debate.phase.completed`
3. **Topic 미존재 시 재생성** (kafka-init 재실행):
   ```bash
   docker compose up -d kafka-init
   ```
4. **Cluster ID mismatch 발생 시**:
   ```bash
   docker compose down -v       # 볼륨까지 삭제
   docker compose up -d --build
   ```

### 사후 분석 (로그/메트릭 확인 절차)
- 누락된 이벤트 식별 (debate.completed):
  ```bash
  docker logs stock-debate-svc | grep "완료 처리" | tail -20
  ```
  → 해당 debateId 가 Kafka에 발행됐는지 consumer 측 로그와 대조.
- Consumer lag (downstream이 있는 경우):
  ```bash
  docker exec stock-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --describe --all-groups
  ```

### 재발 방지 (장기 개선)
- Outbox 패턴 도입: 도메인 트랜잭션과 이벤트 발행을 같은 트랜잭션 경계로 묶음. 현재는 in-memory 저장이라 outbox 효과 제한적이지만 향후 RDB 도입 시 우선 적용.
- Dead Letter Queue (DLQ) 토픽 분리: `debate.completed.dlq` 등.
- Kafka rebalance 시 메시지 유실 여부 — ARCHITECTURE.md §10 잔여 (Week 3 부하 테스트).

---

## S4. KARPATHY 무한 FAIL (재시도 한도 도달)

### 증상 (관측)
- 로그 패턴:
  ```
  [Debate:{uuid}] KARPATHY FAIL — 재시도 #1
  [Debate:{uuid}] KARPATHY FAIL — 재시도 #2
  [Debate:{uuid}] 최대 재시도 초과 — FAILED
  ```
- 사용자 영향: 토론 결과 `status=FAILED`, `DebateFailedEvent(reason="KARPATHY_MAX_RETRY")` 발행.
- 빈도: thesis 자체가 모순적이거나 ticker 데이터가 비정상일 때 발생.

### 원인 (Root Cause)
- thesis 내용이 phase 1~2(AMODEI/ALTMAN) 분석과 phase 3(MUSK) 반론 사이에서 일관성 확보 불가
- LLM 응답 변동성: 같은 thesis인데 일부 시도에서만 FAIL
- KARPATHY 시스템 프롬프트 튜닝 부족

### 코드 처리 위치
- [DebateSession.kt:19](../ai-debate-svc/src/main/kotlin/com/invest/debate/domain/model/DebateSession.kt) — `const val MAX_RETRY = 2` 도메인 상수
- [DebateSession.kt:69](../ai-debate-svc/src/main/kotlin/com/invest/debate/domain/model/DebateSession.kt) — `handleKarpathyFail()`:
  ```kotlin
  retryCount++
  return if (retryCount <= MAX_RETRY) {
      phases.removeAll { it.phaseNum >= 3 }  // MUSK, KARPATHY 제거
      true
  } else {
      status = DebateStatus.FAILED
      false
  }
  ```
  AMODEI(phase 1), ALTMAN(phase 2)은 보존 — Saga compensating transaction 패턴 ([adr/0009](../adr/0009-karpathy-retry-saga.md))
- [DebateOrchestrationService.kt:117](../ai-debate-svc/src/main/kotlin/com/invest/debate/application/service/DebateOrchestrationService.kt) — `handleFail` 분기
- [DebateOrchestrationService.kt:129](../ai-debate-svc/src/main/kotlin/com/invest/debate/application/service/DebateOrchestrationService.kt) — `reason = "KARPATHY_MAX_RETRY"` 이벤트 발행

### 즉시 대응 (운영자 첫 5분)
1. **실패 토론 식별**:
   ```bash
   docker logs stock-debate-svc | grep "KARPATHY_MAX_RETRY" | tail -10
   ```
2. **해당 토론의 thesis/ticker 확인**: 같은 debateId로 phases 출력 추적:
   ```bash
   docker logs stock-debate-svc | grep "Debate:{uuid}" | head -30
   ```
3. **재시도 가능 여부 판단**: 동일 thesis 재요청 시에도 동일하게 FAIL 가능성 높음. thesis 문구 조정 후 재요청 안내가 적절.

### 사후 분석 (로그/메트릭 확인 절차)
- FAIL 비율 산출 (특정 기간):
  ```bash
  docker logs stock-debate-svc --since 1h | grep -c "KARPATHY_MAX_RETRY"
  docker logs stock-debate-svc --since 1h | grep -c "완료 처리"
  ```
- 패턴 분석: 특정 ticker/sector에 집중되는지? thesis 길이/형식 상관관계?
- KARPATHY 출력 샘플 점검: `[Debate:{uuid}] Phase 4 완료 | verdict=FAIL` 로그 → `extractVerdict()` 가 `[판정: FAIL]` 외 다른 텍스트의 "FAIL" 단어를 오감지했는지 확인. ([PhaseOrchestrator.kt:153](../ai-debate-svc/src/main/kotlin/com/invest/debate/application/service/PhaseOrchestrator.kt))

### 재발 방지 (장기 개선)
- KARPATHY 시스템 프롬프트 정교화: `[판정: PASS/HOLD/FAIL]` 첫 줄 강제 형식 명시 (현재 `ClaudeApiAdapter.kt:72`에 기반 있음, 본문 일관성 검증 필요).
- `extractVerdict()` 오탐 방지: 본문에서 "FAIL" 단어가 *판정 외 맥락*에서 등장 시 (예: "실패 가능성") 오인 방지를 위한 패턴 강화.
- thesis 입력 validation: 너무 짧거나 모순적인 thesis는 사전 reject.

---

## S5. EL JSON 파싱 실패

### 증상 (관측)
- 로그 패턴:
  ```
  [EL] JSON 파싱 실패 — fallback 적용. 원본 길이={n}자, 오류={msg}
  ```
- 사용자 영향: `successProbability=50` 디폴트 리포트로 응답. 토론 자체는 `COMPLETED`. 사용자는 정상 응답을 받지만 신뢰도 낮은 결과.
- 빈도: Haiku 모델 기준 ~5% 이내 예상 (실측치 없음).

### 원인 (Root Cause)
- Claude EL 응답에 JSON 앞뒤로 자연어 첨부 (시스템 프롬프트 위반)
- JSON 내부 따옴표 escape 누락
- `actions` 배열 개수 부족/초과

### 코드 처리 위치
- [PhaseOrchestrator.kt:165](../ai-debate-svc/src/main/kotlin/com/invest/debate/application/service/PhaseOrchestrator.kt) — `parseElOutput` 진입
- 같은 파일 line 168-173: **1단계 fallback** — 중괄호 `{...}` 블록만 추출
- 같은 파일 line 175-188: 정상 파싱 + `actions` 정확히 3개 보정 (padding/trimming)
- 같은 파일 line 189-197: **2단계 fallback** — 디폴트 리포트 생성 (`successProbability=50`, dispute "EL 응답 파싱 실패 — 재시도 권장")

### 즉시 대응 (운영자 첫 5분)
1. **파싱 실패 빈도 확인**:
   ```bash
   docker logs stock-debate-svc --since 1h | grep -c "JSON 파싱 실패"
   ```
2. **EL 원본 응답 패턴 점검**:
   ```bash
   docker logs stock-debate-svc | grep -B2 "JSON 파싱 실패" | tail -30
   ```
3. **시스템 프롬프트 위반 확인**: `ClaudeApiAdapter.kt:73-80` EL 시스템 프롬프트가 컨테이너 내 실제 적용됐는지 검증 (배포 누락 흔한 원인).

### 사후 분석 (로그/메트릭 확인 절차)
- 파싱 실패 시 응답 길이 분포: `[EL] ... 원본 길이={n}자` 패턴에서 n 추출 → 평균/최대 분포.
- successProbability=50 인 토론 식별 (디폴트 fallback 값):
  ```bash
  docker logs stock-debate-svc | grep "성공확률=50%" | tail -20
  ```
- 1단계 fallback (중괄호 추출)으로 복구된 경우와 2단계 fallback (디폴트 리포트) 비율: 1단계는 log warn 안 나옴, 2단계만 warn.

### 재발 방지 (장기 개선)
- Anthropic API의 **structured output** / tool use 기능 적용 검토 — JSON 스키마를 API 측에서 보장.
- 시스템 프롬프트 JSON 강제 문구 보강 ("다른 텍스트 절대 금지" 강조 변형 실험).
- 1단계 fallback (중괄호 추출) 성공률 메트릭화: 현재는 로그만으로 추정.

---

## 6. 헬스체크 명령 모음

운영자가 5분 안에 전체 시스템 상태를 파악하기 위한 명령 묶음.

### 6.1 컨테이너 상태
```bash
docker compose ps
docker ps -a --filter name=stock-
```

### 6.2 서비스 헬스 엔드포인트
```bash
# ai-debate-svc (Spring Boot Actuator)
curl -s http://localhost:8083/actuator/health

# market-svc
curl -s http://localhost:8085/actuator/health    # host port 8085 → container 8081
```

### 6.3 Kafka 토픽 + Consumer
```bash
docker exec stock-kafka kafka-topics --bootstrap-server localhost:9092 --list
docker exec stock-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group debate-svc-group
```

### 6.4 Redis
```bash
docker exec stock-redis redis-cli -p 6379 PING            # PONG 기대
docker exec stock-redis redis-cli -p 6379 DBSIZE
```

### 6.5 토론 1회 end-to-end 스모크 테스트
```bash
# market-svc 경유 (Kafka 경로)
curl -X POST http://localhost:8085/debate/request \
  -H "Content-Type: application/json" \
  -d '{"userId":"smoke","symbol":"AAPL","thesis":"AI 수요 견인","price":180.5}'

# 응답의 requestId로 polling
curl -s http://localhost:8083/debate/{requestId}/status
```

---

## 7. 미실증 항목 (cross-link)

본 RUNBOOK은 *코드상 보장 + 운영 절차*를 정리. 실측 검증은 ARCHITECTURE.md §10 잔여 작업 참조.

| 항목 | 본 문서 위치 | 잔여 검증 |
|---|---|---|
| Redis 장애 시 그레이스풀 디그레이드 | §S2 | Week 1 STEP 5 실증 (코드상 `onErrorResume` 동작 실측) |
| Kafka rebalance 메시지 유실 여부 | §S3 | Week 3 부하 테스트 |
| EL 파싱 실패 실측 비율 | §S5 | k6 부하 시나리오 동시 측정 |
| KARPATHY FAIL 비율 분포 | §S4 | 면접後 별도 실험 |

---

## 8. 관련 문서

- [ARCHITECTURE.md](../ARCHITECTURE.md) — §7.3 에러 모드 분석 (본 RUNBOOK의 원본 SSOT)
- [STATUS.md](../STATUS.md) — 현재 진행 상황 + 잔여 STEP
- [adr/0008-kafka-manual-ack-thread.md](../adr/0008-kafka-manual-ack-thread.md) — Kafka manual ack + 별도 Thread 설계 근거
- [adr/0009-karpathy-retry-saga.md](../adr/0009-karpathy-retry-saga.md) — KARPATHY 재시도 = Saga compensating transaction
- [adr/0005-redis-ttl-kafka-cqrs.md](../adr/0005-redis-ttl-kafka-cqrs.md) — Redis TTL 24h 결정 근거

---

*Last updated: 2026-05-14 — Week 1 STEP 5*

# ADR-0006: Claude Haiku 모델 선택 (vs Sonnet)

- **Status**: Accepted
- **Date**: 2026-05-13
- **Deciders**: 박수범 (단독 작업)

## Context

5인 페르소나가 순차 호출이라 latency가 곱셈으로 누적:

- Sonnet 기준: 페르소나당 60~120초 → 전체 5~10분. 모바일 RN polling 환경에서 사용자 이탈.
- Haiku 기준: 페르소나당 10~25초 → 전체 50~125초. polling으로 견딜 만한 범위.

비용 측면 — Haiku는 Sonnet 대비 약 1/10, 토큰당 입출력 단가 차이가 큼. MVP 단계에서 비용 통제가 우선.

## Decision

- 모든 페르소나(AMODEI → ALTMAN → MUSK → KARPATHY → EL)에 `claude-haiku-4-5-20251001` 사용
- `max_tokens = 800` 으로 응답 길이를 절단 (비용·속도 추가 절약)
- 90초 timeout + `retry(1)` 로 네트워크 일시 오류 한정 재시도

prompt engineering으로 모델 다운그레이드 손실을 흡수 — system prompt에 페르소나 역할·출력 형식·"한국어로 응답"을 강하게 명시.

## Consequences

### Positive
- **비용**: Sonnet 대비 약 1/10. 세션당 5번 호출이라 누적 효과 큼.
- **속도**: 전체 토론 시간 1/5 수준. 사용자 대기시간 critical 개선.
- 단순 분석/요약 태스크에는 Haiku 품질이 실용적으로 충분.

### Negative / Trade-offs
- KARPATHY QA 판정에서 Sonnet 대비 정확도 손실 가능성 → 무한 FAIL → MAX_RETRY 초과 케이스 증가 우려 (실측 필요, STEP 4 잔여).
- 깊은 추론이 필요한 EL 종합 단계에서 합의·액션 품질 저하 가능성.

## Alternatives Considered

- **전체 Sonnet** — 비용·속도 모두 안 맞음. MVP 단계에서 과함. 기각.
- **전체 Opus** — 가장 비싸고 느림. 기각.
- **Hybrid (AMODEI·ALTMAN·MUSK·EL Haiku + KARPATHY Sonnet)** — KARPATHY 판정 정확도가 전체 retry 비용을 좌우하므로 cost-effective 후보. 미구현, STEP 4·5 잔여 검토.

## References

- 코드:
  - [ClaudeApiAdapter.kt:19, 40, 54, 61](../ai-debate-svc/src/main/kotlin/com/invest/debate/infrastructure/llm/ClaudeApiAdapter.kt) (model · maxTokens · timeout · retry)
  - [application.yml:30-33](../ai-debate-svc/src/main/resources/application.yml) (model 설정 + 비교 주석 `Sonnet → Haiku: 5~10배 빠름, 1/10 비용`)
- 관련 ADR: [0001 (WebFlux)](0001-spring-webflux.md) (long-tail API 호출 reactive 처리)

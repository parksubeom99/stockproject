# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

StockProject is an AI investment-analysis platform built around a 5-persona AI debate. A React Native (Expo SDK 51) app talks to two Kotlin Spring WebFlux services connected by Kafka, with Redis as the shared cache/persistence store. The thesis is to remove confirmation bias by forcing 5 personas (AMODEI → ALTMAN → MUSK → KARPATHY → EL) to argue sequentially before a decision is rendered.

## Service Topology

```
RN app ──WS──▶ market-svc :8081 ──Kafka(debate.requested)──▶ ai-debate-svc :8083
       ──HTTP─────────────────────────────────────────────▶ ai-debate-svc :8083
                       │                                         │
                       └──── Redis :6380 ◀────────── (sessions + quote cache)
```

- `market-svc` (Kotlin, port 8081): WebSocket streaming of quotes (3s interval, `distinctUntilChanged`), Redis quote cache, publishes `debate.requested` to Kafka. JWT-gated WebSocket handshake via `WebSocketHandshakeInterceptor` (WebFilter). Inside Docker `market-svc` listens on `8081` but is exposed to the host as **`8085`** (see `docker-compose.yml`); the README's `8081` is the container-internal port.
- `ai-debate-svc` (Kotlin, port 8083): Consumes `debate.requested`, runs `PhaseOrchestrator` over the 5 personas, persists sessions in Redis (prod) or in-memory (mock/test). Publishes `debate.completed` / `debate.failed`.
- `stock-app` (Expo RN): 3 screens (Quotes, DebateRequest, DebateResult), connects to backends via `src/constants/api.ts`. Android emulator alias `10.0.2.2` is hard-coded — change `BASE_HOST` for physical devices.

Kafka topics are auto-created plus explicitly created by the `kafka-init` one-shot container: `debate.requested`, `debate.completed`, `debate.failed`, `debate.phase.completed`.

## Architecture Conventions

Both Kotlin services follow **DDD + Hexagonal**:

- `domain/` — entities, value objects, ports (inbound `*UseCase`, outbound `*Port`). `DebateSession` uses a private constructor with `create()` / `reconstruct()` factories so Redis snapshots round-trip without bypassing invariants. `DebateId = typealias UUID`.
- `application/` — `@Service` classes implementing inbound ports and orchestrating outbound ports. `DebateOrchestrationService` is the entrypoint for both REST (`autoOrchestrate=true`) and Kafka (`autoOrchestrate=false`, Consumer calls `orchestrate()` on its own thread after ack).
- `infrastructure/` (ai-debate-svc) or `adapter/` (market-svc) — Kafka, Redis, WebSocket, Web, LLM adapters.

### Profile-based adapter swap

Adapters are switched by Spring profile, not by config keys. When adding new external integrations, follow this pattern:

| Port | mock / test / default | prod |
|---|---|---|
| `LlmPort` | `MockLlmAdapter` (`@Profile("mock")`, 500ms canned response) | `ClaudeApiAdapter` (`@Profile("prod")`, WebClient → `api.anthropic.com`) |
| `DebateSessionRepository` | `InMemoryDebateSessionRepository` (`@Profile("!prod")`) | `RedisDebateSessionRepository` (`@Profile("prod")`, 24h TTL, JSON snapshots) |

Docker Compose runs `ai-debate-svc` with `SPRING_PROFILES_ACTIVE=prod` (real Claude API + Redis) and `market-svc` with `mock` (synthetic quotes via `MockStockQuoteAdapter`).

### Debate flow invariants

- `PhaseOrchestrator.runPhases` runs AMODEI → ALTMAN → MUSK → KARPATHY sequentially, then EL. **Each phase only sees previous outputs via `session.buildContext()`** — this partial-visibility rule is the whole point of the design; don't broaden it.
- KARPATHY verdict is parsed from text (`FAIL` / `HOLD` / `PASS`). On `FAIL`, `DebateSession.handleKarpathyFail()` drops phases ≥ 3 and re-runs from MUSK; `MAX_RETRY = 2`.
- EL must emit strict JSON (`consensus`, `successProbability` 0–100, `disputes`, `actions`). `parseElOutput` brace-extracts and falls back to a 50% canned report on parse failure. `DebateReport` invariant: **exactly 3 actions** — padding/trimming happens inside the parser.
- RN polls `GET /debate/{id}/status` every ~3s. To keep that polling stable, the Kafka consumer reuses the producer's `requestId` (as UUID) as the `debateId` via `DebateSession.create(externalId=...)`. Don't generate a new UUID in the consumer path.

## Common Commands

```bash
# Full stack (Kafka + ZooKeeper + Redis + both services)
docker compose up -d --build
docker compose down -v          # wipe volumes; needed when Kafka cluster id mismatches on rebuild
docker logs stock-debate-svc --tail 100
docker logs stock-market-svc --tail 100

# Backend tests (Gradle wrapper, JUnit 5 + MockK + reactor-test)
cd ai-debate-svc && ./gradlew test
cd market-svc    && ./gradlew test

# Single test class / method
./gradlew test --tests "com.invest.debate.application.service.PhaseOrchestratorTest"
./gradlew test --tests "*PhaseOrchestratorTest.should run all 5 phases*"

# Local run without Docker (mock profile by default in application.yml)
cd ai-debate-svc && ./gradlew bootRun
cd market-svc    && ./gradlew bootRun

# RN app (Android emulator; uses 10.0.2.2)
cd stock-app && npx expo start --android
# iOS / physical device: edit src/constants/api.ts BASE_HOST before starting
```

There is **no lint or formatter wired up** for either Gradle module or the Expo app. The RN app has no `test` script.

## Endpoints (quick reference)

| Method | URL | Notes |
|---|---|---|
| WS | `ws://localhost:8081/ws/quotes/{symbol}?token=dev-token` | `dev-token` query param only works under `mock` profile; prod requires HS256 JWT signed with `WEBSOCKET_JWT_SECRET` |
| POST | `http://localhost:8081/debate/request` | market-svc → publishes Kafka `debate.requested`, returns `{requestId}` to use as `debateId` for polling |
| POST | `http://localhost:8083/debate/start` | ai-debate-svc REST direct path; auto-orchestrates in a background thread |
| GET | `http://localhost:8083/debate/{id}/status` | Returns `IN_PROGRESS` (when report missing) or `COMPLETED` — RN polling endpoint |
| GET | `http://localhost:8083/debate/{id}/report` | 404-equivalent (`NoSuchElementException`) until status is `COMPLETED` |

## Working in this Repo

- `.gitattributes` enforces LF for source/config and CRLF only for `*.bat`/`*.cmd`. Don't fight the normalization.
- `.gitignore` excludes `.env`, `.claude/`, `build/`, `node_modules/`, `.expo/`. Never commit `.env` — `ANTHROPIC_API_KEY` is required only when running `ai-debate-svc` under `prod`.
- The `ai-debate-svc/docker-compose.yml` and `market-svc/docker-compose-market.yml` are **per-service** legacy files; the root `docker-compose.yml` is the authoritative one for full-stack runs.
- Kafka consumer uses manual ack (`enable-auto-commit: false`) — preserve `ack.acknowledge()` ordering when editing `DebateRequestedConsumer`; orchestration is intentionally fired on a separate `Thread` after ack so consumer poll loop isn't blocked.
- Korean comments and log messages are intentional throughout the codebase. Don't translate them when making unrelated edits.

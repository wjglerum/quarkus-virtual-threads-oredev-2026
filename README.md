# Concurrency Crossroads: Reactive vs Virtual Threads

A Quarkus demo exploring five concurrency models through a single domain: **The Coffee Shop** — a REST API where bartenders brew coffee (a slow HTTP call) and persist the result to a database.

![Architecture](diagram-simple.svg)

Each approach uses the same business logic so the trade-offs are immediately comparable.

---

## Prerequisites

| Requirement | Version |
|---|---|
| JDK | 27 with preview features enabled |
| Docker or Podman | running, with the `docker compose` plugin |
| Maven | bundled via `./mvnw` |

> **Why `--enable-preview`?** Structured Concurrency (`StructuredTaskScope`) is a preview feature in Java 27 (7th preview, JEP 533; final in JDK 28 via JEP 543). The whole project compiles against Java 27 with preview enabled.

All external dependencies (PostgreSQL, WireMock, Grafana/LGTM) are started automatically as **DevServices** when you run in dev mode — no manual setup required. In dev mode the coffee machine runs as a standalone WireMock container via Compose Dev Services (`src/main/docker/compose-dev.yml`) with async responses and 500 threads, so WireMock never caps the flood demos.

---

## Running

```shell
./mvnw quarkus:dev
```

- App: <http://localhost:8080>
- Dev UI: <http://localhost:8080/q/dev/>
- Grafana (LGTM): check the Dev UI for the port

Use `examples.http` with your IDE HTTP client (IntelliJ, VS Code REST Client) to fire requests.

---

## The Five Approaches

![Concurrency models](diagram.svg)

### 01 · Blocking — Platform Threads

> `GET /beverage/blocking`

The traditional Java model. Each request runs on a platform (OS) thread from the Quarkus worker pool. The thread is **blocked** for the entire duration of the coffee machine call (~3 s in dev).

- Simple, familiar code — no special annotations
- Thread pool exhaustion is visible: flood the endpoint and watch requests fail once the pool is full
- Uses `@Transactional` + Hibernate ORM

| Endpoint | Description |
|---|---|
| `/beverage/blocking` | Single request |
| `/beverage/blocking/sequential` | 3 requests, one after another |
| `/beverage/blocking/parallel` | 3 requests via `ManagedExecutor` |
| `/beverage/blocking/flood?count=100` | Fans out 100 tasks on a fixed pool of 50 threads + 25 queued — shows saturation |
| `/beverage/blocking/failfast` | 3 parallel flakey calls — first failure returns 503, siblings keep running |

---

### 02 · Reactive — Mutiny + Vert.x

> `GET /beverage/reactive`

The reactive model. Requests run on the Vert.x event loop; the thread is **never blocked** — it returns immediately and a callback fires when the response arrives.

- Maximum throughput on a small number of threads
- Code complexity: nested `Uni` chains instead of straight-line code
- Uses `@Transactional` + Hibernate Reactive with Panache (via the `quarkus-reactive-transactions` extension)

| Endpoint | Description |
|---|---|
| `/beverage/reactive` | Single request |
| `/beverage/reactive/sequential` | 3 chained `Uni` calls |
| `/beverage/reactive/parallel` | 3 parallel `Uni` calls via `Uni.join()` |
| `/beverage/reactive/flood?count=100` | Fans out 100 non-blocking calls |
| `/beverage/reactive/failfast` | 3 parallel flakey calls via `Uni.join().andFailFast()` — first failure cancels the join |

---

### 03 · Virtual Threads — `@RunOnVirtualThread`

> `GET /beverage/virtual`

Virtual threads (JEP 444). Each request runs on a **virtual thread** — cheap, JVM-managed, non-blocking under the hood. The code looks identical to the blocking approach, but threads yield to the carrier during I/O instead of blocking it.

- Straight-line blocking code, reactive scalability
- Annotate the class with `@RunOnVirtualThread` — that's it
- Uses `@Transactional` + Hibernate ORM (same as blocking)

| Endpoint | Description |
|---|---|
| `/beverage/virtual` | Single request |
| `/beverage/virtual/sequential` | 3 sequential calls |
| `/beverage/virtual/parallel` | 3 parallel calls via injected `@VirtualThreads` executor |
| `/beverage/virtual/custom` | 3 parallel calls with a named custom thread factory |
| `/beverage/virtual/flood?count=100` | Fans out 100 tasks on virtual threads — all succeed |
| `/beverage/virtual/race` | 3 bartenders raced with `CompletableFuture.anyOf` — the losers keep brewing |
| `/beverage/virtual/failfast` | 3 parallel flakey calls — first failure returns 503, siblings keep running |

---

### 04 · Structured Concurrency — `StructuredTaskScope`

> `GET /beverage/structured`

Structured Concurrency (JEP 533, 7th preview in JDK 27; final in JDK 28). Subtasks are **scoped to a parent task** — when the scope closes, all subtasks are guaranteed to be done or cancelled. Enables race, fail-fast, and timeout patterns without manual thread management.

- Lifetimes are explicit and safe: no orphaned threads
- Cancellation propagates automatically to sibling tasks
- Uses `StructuredTaskScope.open()` with different joiners

| Endpoint | Joiner | Description |
|---|---|---|
| `/beverage/structured/simple` | default | 3 parallel tasks, collect all |
| `/beverage/structured/custom` | `allSuccessfulOrThrow()` | Same with a named thread factory |
| `/beverage/structured/race` | `anySuccessfulOrThrow()` | First bartender to finish wins, siblings cancelled |
| `/beverage/structured/race/custom` | custom `FirstSuccessJoiner` | Same race with a hand-written Joiner and random 1–3 s brews — compare with `/beverage/virtual/race` |
| `/beverage/structured/failfast` | `allSuccessfulOrThrow()` | One failure cancels everyone (uses a flakey bartender, 50% chance) |
| `/beverage/structured/timeout` | `allSuccessfulOrThrow()` + `withTimeout` | Scope cancelled after 150 ms, returns 504 |

---

### 05 · Pinning — `synchronized` vs `ReentrantLock`

> `GET /beverage/pinning`

**Historical context:** in Java 21–23, a virtual thread holding a `synchronized` monitor during I/O would **pin** to its carrier thread — blocking it like a platform thread and defeating the scalability benefit. The standard workaround was to replace `synchronized` with `ReentrantLock`.

**JEP 491 (Java 24)** fixed this: `synchronized` no longer pins. Both patterns yield the carrier freely on Java 24+, as confirmed by `@ShouldNotPin` on all four tests.

- `PinningBartender` uses `synchronized` — the old culprit, now safe
- `UnpinningBartender` uses `ReentrantLock` — the old workaround, still valid
- Run with `-Djdk.tracePinnedThreads=short` (already configured) to observe zero pinning events

| Endpoint | Description |
|---|---|
| `/beverage/pinning/pinned` | Single call through `synchronized` |
| `/beverage/pinning/unpinned` | Single call through `ReentrantLock` |
| `/beverage/pinning/pinned/parallel` | 3 parallel calls through `synchronized` |
| `/beverage/pinning/unpinned/parallel` | 3 parallel calls through `ReentrantLock` |

---

## DevServices

Everything starts automatically in dev mode:

| Service | What it does |
|---|---|
| **PostgreSQL** | Database for all five approaches |
| **WireMock** | Mocks the coffee machine with a 3 s delay, plus a random 1–3 s endpoint for races (100 ms in tests) |
| **LGTM** | Grafana + Loki + Tempo + Prometheus — metrics via Micrometer + OTel |

In Grafana (link in the Dev UI, or the "Dev Service Lgtm started" log line) open the **Concurrency Crossroads** dashboard. During the blocking flood platform threads jump by ~50; during the virtual flood they stay flat while the carrier pool stays at the CPU count. Metrics are exported every 2 s in dev.

---

## Running Tests

```shell
./mvnw test
```

Tests use WireMock with a 100 ms delay. Virtual thread tests are annotated with `@ShouldNotPin` to assert that no unexpected carrier pinning occurs.

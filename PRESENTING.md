# Presenting the demos

Checklist for running the four live demos on stage. The requests are in [`demo.http`](demo.http), in talk order.

## Running order

The 40 slides that are shown, with the demos in place. Slide numbers are the deck's own numbers, so they skip the hidden slides. Slides marked *brief* are there for context: give each one a sentence or two and move on.

| Slide | Title | Demo |
|---|---|---|
| 1–4 | Title, What you get today, whoami, Agenda | |
| 5–7 | **01 Introduction:** A short history of Java concurrency, Three threading models | |
| 8–11 | **02 Blocking I/O:** I/O is everywhere, Platform threads, Blocking I/O | |
| 12 | Demo App: The Quarkus Coffee Shop | |
| 13 | Demo: Blocking I/O | **1** · requests 1–2 |
| 14, 15 | **03 Reactive Programming:** Reactive systems *(brief)* | |
| 17, 18 | Reactive programming, Non-blocking I/O | |
| 20, 22, 23, 25 | Unification: Imperative and Reactive, Your options in Quarkus, Mutiny: Uni & Multi, Quarkus reactive extensions *(all brief)* | |
| 26 | Demo: The reactive coffee shop | **2** · requests 3–4 |
| 27 | Reactive gotchas | |
| 28 | Real examples from production *(brief, no live request)* | |
| 29, 30, 32, 33 | **04 Virtual Threads:** Virtual threads, Mount and unmount, Virtual threads (one carrier, three virtual threads) | |
| 37, 38 | What virtual threads give you, Pinning: the synchronized story | |
| 45 | Demo: Imperative on virtual threads | **3** · requests 5–6 |
| 51, 52 | **05 Structured Concurrency:** Why structured concurrency | |
| 54 | Demo: First success, cancel the rest | **4** · request 7 |
| 65, 66 | **06 Comparison & Conclusions:** Where Quarkus sits | |
| 68, 69 | Comparison, A decision framework | |
| 70, 71 | References, Thank you (questions) | |

## The evening before (on good Wi-Fi)

1. `sdk env` → should select `27.0.0.0+35-tem`; check with `java -version`.
2. `./mvnw verify` → runs the test suite on your own laptop (JDK 27, Docker).
3. `docker pull wiremock/wiremock:3.13.2`, then `./mvnw quarkus:dev` and wait until all Dev Services are up. This pulls the PostgreSQL and `grafana/otel-lgtm` images (the LGTM image is over 1 GB) and fills the local Maven repository, so the next start can run offline.
4. `docker images` → confirm `wiremock/wiremock`, `postgres` and `grafana/otel-lgtm` are cached.
5. Run all 7 requests in `demo.http` and take a screenshot of each result (response + console). Keep them as fallback slides.

## 30 minutes before the talk

1. Wi-Fi off. Keep the laptop awake (`caffeinate -dims &` on macOS) and make sure Docker Desktop's Resource Saver is off.
2. `./mvnw -o quarkus:dev` (offline). Never start the app or the containers during the talk.
3. Warm up by running all 7 requests in `demo.http` once.
4. Windows in order: slides, IDE with `demo.http` and the dev console (font ≥ 20). Do Not Disturb on.

## Things that behave differently than you might expect

| Demo | What happens | What to do |
|---|---|---|
| 1, 3 · Floods | The response is JSON: `succeeded`, `failed` and `durationMs`. The blocking flood is always 75 succeeded / 25 failed in ~6 s (50 threads + 25 queued, two waves of 3 s); the virtual flood is 100 / 0 in ~3 s. | Read the three numbers out loud; there's no Grafana in this version of the talk. |
| 2 · Reactive sequential | Takes ~9 s: three brews one after another. | Keep talking while it runs. |
| 4 · Structured race | Brews take a random 1–3 s, so the winner changes per run. Threads are named `bartender-1..3`; the losers log "Cancelled after N ms, stopped brewing", then "Scope closed". Covered by `StructuredBeverageResourceTest#testStructuredRaceCancelsTheLosers`. | — |

## If a demo dies (one-minute fallback)

Show the screenshot from the night before and say the numbers:

1. **Blocking:** one coffee in 3 seconds on a thread that waits the whole time; the flood gives 75 OK and 25 failed in 6 seconds.
2. **Reactive:** sequential takes 9 seconds, parallel 3 seconds, all on the event loop.
3. **Virtual threads:** same code as blocking, the flood gives 100 OK in 3 seconds.
4. **Structured concurrency:** one "Served", the others "Cancelled after N ms", then "Scope closed".

# Presenting the demos

Checklist for running the four live demos on stage. The requests are in [`demo.http`](demo.http), in talk order.

## The evening before (on good Wi-Fi)

1. `sdk env` → should select `27.0.0.0+35-tem`; check with `java -version`.
2. `./mvnw verify` → runs the test suite on your own laptop (JDK 27, Docker).
3. `docker pull wiremock/wiremock:3.13.2`, then `./mvnw quarkus:dev` and wait until all Dev Services are up. This pulls the PostgreSQL and `grafana/otel-lgtm` images (the LGTM image is over 1 GB) and fills the local Maven repository, so the next start can run offline.
4. `docker images` → confirm `wiremock/wiremock`, `postgres` and `grafana/otel-lgtm` are cached.
5. Run all 8 requests in `demo.http` and take a screenshot of each result (console + Grafana). Keep them as fallback slides.

## 30 minutes before the talk

1. Wi-Fi off. Keep the laptop awake (`caffeinate -dims &` on macOS) and make sure Docker Desktop's Resource Saver is off.
2. `./mvnw -o quarkus:dev` (offline). Never start the app or the containers during the talk.
3. Open the Dev UI → LGTM → **Concurrency Crossroads** dashboard, time range *Last 5 minutes*, zoom ~150%. The Grafana port changes on every start, so keep this tab open.
4. Warm up with requests 1, 3, 4, 5 and 6, then press `s` in the dev console to restart the app (see the Grafana note below). Re-run request 1 only.
5. Windows in order: slides, IDE with `demo.http` and the dev console (font ≥ 20), Grafana. Do Not Disturb on.

## Things that behave differently than you might expect

| Demo | What happens | What to do |
|---|---|---|
| 1 · Blocking flood | The flood pool (50 threads + 25 queued) keeps its threads, so the "+50 platform threads" jump in Grafana only shows on the **first** flood after start. Result is exactly 75 OK / 25 rejected in ~6 s. | After rehearsing, press `s` in the dev console to restart the app. Don't run the blocking flood again until you're on stage. |
| All floods | Metrics export every 2 s and the dashboard refreshes every 5 s. | Keep talking ~5 s after the flood returns, then switch to Grafana. |
| 2, 3 · Reactive and virtual floods | Platform threads don't drop back after demo 1 (the flood pool is still alive); virtual-thread carriers can add up to one per CPU core. | Say "no new platform threads beyond the carriers", not "flat". |
| 4 · Virtual race | The losers' "Served a virtual coffee after N ms" lines arrive 0–2 s after the response. | Pause before pointing at the console. |
| 4 · Structured race | Threads are named `bartender-1..3`; the losers log "Cancelled after N ms, stopped brewing", then "Scope closed". Covered by `StructuredBeverageResourceTest#testStructuredRaceCancelsTheLosers`. | — |
| 4 · Fail-fast | Per run: ~75% a 503 that visibly cancels siblings, ~12.5% a 503 with nothing left to cancel, ~12.5% a 200. | Run it twice at most. On a 200: "one in eight the coffee machine is fine — that's why you need fail-fast." |

## If a demo dies (one-minute fallback)

Show the screenshot from the night before and say the numbers:

1. **Blocking:** 75 OK, 25 rejected, 6 seconds, +50 platform threads.
2. **Reactive:** 100 OK in 3 seconds on a handful of event-loop threads.
3. **Virtual threads:** same bartender code, 100 OK in 3 seconds, no new platform threads.
4. **Structured concurrency:** one "Served", the others "Cancelled after N ms", then "Scope closed".

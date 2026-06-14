# rock-metrics

> Platform **observability** (RPS §13): counters/timers across ROCK, surfaced
> in-game and fed to the web dashboard.

- **Module id:** `rock-metrics` · **Depends:** rock-core
- **Backed by:** the platform `MetricsRegistry` (rock-core)
- **Status:** functional (in-game report); **early** (§4).

## 1. What it does
The platform records metrics (command counts, DB timings, event throughput,
etc.) into a `MetricsRegistry`; rock-metrics exposes a readable report and makes
the data available to rock-web.

## 2. Command
| Command | Permission | Behaviour |
|---|---|---|
| `/rock metrics` | `rock.admin.metrics` | Prints a platform metrics report. |

**[QoL gaps]:** plain text (no formatting/paging/clickable); single snapshot
(no rates/history); no filtering by subsystem.

## 3. Status — public-readiness gaps
- **Export endpoint** (Prometheus `/metrics`, or OpenTelemetry) for real
  monitoring stacks — the actual production value.
- More instrumentation (per-module timings, TPS/MSPT, pool stats, cache hit
  rates) and a web dashboard metrics view with graphs.
- Alerting hooks (e.g. slow query, pool exhaustion → Discord).

## 4. API
```java
MetricsRegistry m = registry.require(MetricsRegistry.class);
m.counter("...").increment(); m.timer("...").record(() -> ...);
```

## Roadmap / upgrade log
- _2026-06-14 · v2.0.0-insiders · documented baseline._

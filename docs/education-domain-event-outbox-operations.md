# Education domain-event outbox operations

`edu_domain_event_outbox` is the education webhook delivery queue. It is
independent from `wf_outbox` and is populated by the existing domain-event
listeners without changing grade, home-school, or supervision contracts.

## Deployment configuration

| Variable | Default | Meaning |
| --- | --- | --- |
| `CHRONOS_DOMAIN_EVENTS_WEBHOOK_URL` | empty | Destination URL. Empty keeps events durable and disables delivery. |
| `CHRONOS_DOMAIN_EVENTS_BATCH_SIZE` | `50` | Maximum rows claimed per dispatch tick. |
| `CHRONOS_DOMAIN_EVENTS_MAX_ATTEMPTS` | `10` | Attempts before the dispatcher marks an event `DEAD`. |
| `CHRONOS_DOMAIN_EVENTS_LEASE_SECONDS` | `120` | Claim lease. An expired `PROCESSING` row is eligible for recovery. |
| `CHRONOS_DOMAIN_EVENTS_DISPATCH_DELAY_MS` | `5000` | Fixed delay between dispatch ticks. |
| `CHRONOS_METRICS_PROMETHEUS_ENABLED` | `false` | Enables the Micrometer Prometheus registry when that registry is deployed. |

The webhook receiver must treat `Idempotency-Key` as the event's
deduplication key. Delivery is at-least-once: a timeout after the receiver
accepted a request can result in a replay.

## Operations API

All endpoints require `education:domain-event:manage` (granted by migration
`V20261202` to platform, super, and education administrator roles).

* `GET /admin/education/domain-events/outbox?status=DEAD&page=0&size=50`
  lists delivery metadata without exposing the JSON payload.
* `POST /admin/education/domain-events/outbox/{id}/replay` makes a failed or
  dead row immediately eligible for delivery and clears its terminal error.
* `POST /admin/education/domain-events/outbox/{id}/dead?reason=...` marks a row
  terminally `DEAD` and records the operator reason.

Replay does not delete or create a row, so the original deduplication key and
audit trail remain intact. Manual replay can still become `DEAD` again after
the configured attempt limit.

## Health and metrics

`/actuator/health/readiness` includes `domainEventOutbox` and reports pending,
processing, and dead counts. `/actuator/health/domainEventOutbox` exposes the
same component directly. Dispatcher counters are available through the
Actuator metrics endpoint as `chronos.domain_events.dispatch.claimed`,
`chronos.domain_events.dispatch.sent`, and
`chronos.domain_events.dispatch.failed`.

The health indicator is database-backed; it is intentionally not a guarantee
that the remote webhook is reachable. Keep the destination protected with
network policy and monitor `failed`, `DEAD`, and lease-recovery volume.

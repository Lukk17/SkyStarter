# 0006 — Tracking event processor with segmentation

- **Status:** Accepted
- **Date:** 2025-11-15
- **Deciders:** Project maintainer
- **Tags:** axon, scaling, eventual-consistency

## Context and problem statement

Projections must keep up with the event stream as throughput grows. Axon offers two processing modes:

- **Subscribing**: events are pushed synchronously inline with command processing — simple, but it ties write latency to projection latency, and a single processor cannot be parallelised across instances.
- **Tracking**: a checkpointed consumer that pulls events from the store independently of the writer; supports segmented parallelism.

We also need a sane parallelism story for horizontal scaling.

## Decision drivers

- Multiple service instances will run in parallel in production.
- Per-aggregate event order must be preserved.
- Adding pods should not require config changes.
- A pod restart must not lose projection progress.

## Considered options

1. **Subscribing event processor** — synchronous, simple, no horizontal scaling story.
2. **Tracking event processor with one segment** — async, but no parallelism.
3. **Tracking event processor with N segments** rebalanced across instances.

## Decision

We chose **option 3** for the `sky-projection-processor`:

```yaml
axon:
  eventhandling:
    processors:
      "sky-projection-processor":
        mode: tracking
        initial-segment-count: 8   # rounded up to 16 by Axon
```

A single pod claims all segments; additional pods cause segments to rebalance automatically (e.g. 2 pods → 8 segments each). All events for a given aggregate hash to the same segment, so per-aggregate order is preserved.

## Consequences

### Positive

- Writes are not blocked by projection latency.
- Horizontal scaling works without code changes — just add pods.
- Restarts are safe: the tracking token is checkpointed in the event store.

### Negative / accepted trade-offs

- Eventual consistency window between write and read (the central trade-off — see [ADR-0004](0004-mongodb-read-projections.md)).
- Segments are allocated at first run; changing the count later requires a token reset for that processor.
- Initial segment count of 8 (rounded to 16) is a starting point; tune per workload.

## Links

- [arc42 §5.3](../arc42/arc42.md#53-infrastructure-level-2), [§11](../arc42/arc42.md#11-risks-and-technical-debt)
- [Axon docs — Tracking event processors](https://docs.axoniq.io/reference-guide/axon-framework/events/event-processors/streaming)

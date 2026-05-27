# Architecture documentation

This directory holds the architecture artefacts for **SkyStarter** — a Spring Boot 3 / Java 21 reference template for event-sourced services.

```
docs/architecture/
├── arc42/        Long-form arc42 documentation (12 sections)
├── diagrams/     Mermaid diagrams referenced from arc42 + ADRs
└── decisions/    Architecture Decision Records (ADRs, MADR format)
```

## Layout

- **`arc42/`** — `arc42.md` is the single-document arc42 template, with cross-links into `diagrams/` and `decisions/`.
- **`diagrams/`** — All Mermaid diagrams. Each file is a single, self-contained `.md` whose only purpose is to embed one diagram so it can be referenced from multiple places without duplication.
- **`decisions/`** — One ADR per file, MADR-style (`NNNN-<short-slug>.md`). New decisions get the next sequential number; superseded ADRs stay (with a `Status: Superseded by NNNN` line) so the audit trail is preserved.

## Where to start

1. Read `arc42/arc42.md` end-to-end for the architecture overview.
2. Skim the diagrams in `diagrams/` for visual context.
3. Read the ADRs in `decisions/` chronologically to understand *why* the architecture looks the way it does.

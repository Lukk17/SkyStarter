# Container view (C4 level 2)

Runtime containers and their primary collaborators.

```mermaid
flowchart LR
    subgraph SkyStarter [SkyStarter JVM]
        api[REST API<br/>StarterController]
        cmd[CommandGateway]
        agg[SkyAggregate<br/>Axon]
        qry[QueryGateway]
        proj[SkyProjection<br/>tracking processor]
    end

    pg[(PostgreSQL<br/>events + snapshots)]
    mongo[(MongoDB<br/>skyProjections)]
    kc[Keycloak]

    api -->|@PreAuthorize| cmd
    api --> qry
    cmd --> agg
    agg -- apply event --> pg
    pg -- tracking stream --> proj
    proj --> mongo
    qry --> proj
    proj --> mongo
    api -. JWT validation .-> kc
```

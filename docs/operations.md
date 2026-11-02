# Production runbook

## Release gates

Require a successful PostgreSQL CI run for the exact release commit. Review migrations and take a verified backup before changing a populated schema. Run the latest dependency/container vulnerability scan, resolve release-blocking findings and attach the report. The project pins the latest available Spring Boot 3.5 patch at the time of this change; confirm a supported release line or commercial support plan before deployment. Automated dependency updates do not establish ongoing support by themselves.

Commission product controls for the deployment: maker-checker, identity verification, sanctions/AML, settlement/reconciliation, interest and fees, account lifecycle, legal reporting and customer access ownership where applicable. Validate them with real provider sandboxes. The included staff KYC decision and local ledger are not substitutes.

## Configuration and credentials

Use the production profile with HTTPS JWT_ISSUER_URI, JWT_JWK_SET_URI and JWT_AUDIENCE. The configured JWKs must belong to the trusted issuer. Test key rotation and issuer outages. Prevent accidental local profile activation in deployment policy. API docs are disabled in production.

Use DATABASE_URL with PostgreSQL TLS certificate verification (`sslmode=verify-full` and your trust configuration), DATABASE_USER and DATABASE_PASSWORD from a secrets manager. Use a separate migration identity; runtime users must not own tables or have DDL/superuser rights. The bootstrap SQL is a starting permission template to review with your DBA. Do not run it against an unrelated database.

Run `mvn flyway:migrate` with MIGRATION_DATABASE_URL, MIGRATION_DATABASE_USER and MIGRATION_DATABASE_PASSWORD set in the release environment, then deploy the same artifact with FLYWAY_ENABLED=false. Migrations are forward-only; `clean` is disabled. Do not edit an applied migration. Hibernate validates the schema at startup. The production compose template requires a prebuilt image and external database; local compose is for a laptop only.

Set application connection budgets and PostgreSQL lock_timeout/statement_timeout per runtime role. Example baseline for DBA review: `ALTER ROLE banking_app SET lock_timeout = '5s'; ALTER ROLE banking_app SET statement_timeout = '15s';`. Connection pool exhaustion should return a controlled failure and alert; never compensate an ambiguous transfer with a blind duplicate request.

## Deployment and traffic

Build and scan an image, publish an immutable digest, run migrations, deploy a canary and check health plus a controlled synthetic workflow. Kubernetes uses a non-root user, read-only root filesystem, resources and separate liveness/readiness probes. Configure private ingress, TLS, gateway rate limits and a 1 MiB body limit; application JSON depth/string/number limits are defense in depth, not a total request-body limit. Configure network policy, trusted proxy headers, OIDC egress and database access for your cluster. Restrict management access. Rollback application code only when it remains compatible with the current schema.

Liveness checks process health; readiness includes database reachability. Graceful shutdown lets in-flight transactions finish. Route traffic away before terminating replicas and allow a sufficient termination grace period.

## Monitoring and incident handling

Scrape /actuator/prometheus with a dedicated MONITORING token. Track HTTP failure/latency, connection-pool saturation, lock waits/deadlocks, database storage/replication, unusual teller operations and failed authorization. Alert on journal reconciliation differences and outbox age once a dispatcher is commissioned. Outbox dispatch is not implemented in this version; records remain pending.

Request IDs connect response headers, application logs and audit events. Restrict logs and database audit access. Export audit records to a separately controlled, tamper-evident archive; a database owner can bypass ordinary protections. Define retention and deletion policy with legal/compliance before operation.

On an ambiguous payment response, look up the original operation using its stable idempotency key through an authorized operational reconciliation process. Retry the same request/key only; never generate a replacement key automatically. Inspect ledger and settlement references. Do not manually edit posted journal entries; implement authorized, linked reversal postings for corrections. A public reversal workflow is not yet implemented.

## Backup, recovery and recovery targets

Set business-approved RPO/RTO; do not infer them from this demo. Use encrypted backups plus PostgreSQL WAL/PITR, store copies outside the primary failure domain and run restore drills. After restore, compare posting counts, journal balance, account balances, audit continuity and provider settlement statements. Fence writers before failover and reconcile ambiguous in-flight transactions before reopening payment traffic.

## External systems

The Fineract HTTP client is optional and is not authoritative for local balances. No automatic Fineract command delivery or reconciliation exists. Kafka delivery, Redis reference caching and OpenTelemetry export need explicit adapters and environment-specific commissioning. Keep these gaps visible in release decisions.

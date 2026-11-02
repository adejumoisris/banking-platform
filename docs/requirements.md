# Capability and readiness matrix

| Area | Implemented | Remaining work |
|---|---|---|
| Java/Spring/JPA/REST | Focused services, ports/adapters, DTOs, typed state and domain rules | Supported-version lifecycle and operational commissioning |
| PostgreSQL | Flyway upgrades, constraints, lock refresh, deferred balanced journal, immutable posted records | Real workload tuning, HA and restore drills |
| Onboarding/accounts | Staff onboarding, manual compliance decision, currency accounts | Identity provider integration, lifecycle, customer ownership |
| Payments | Atomic teller postings and internal transfers, retries, decimal/overflow protection | External collection/settlement, reversals, reconciliation |
| Lending | Principal-only origination, activation, repayment | Underwriting, schedules, interest, fees, delinquency and approvals |
| Ledger | Balanced transactional entries and bounded reads | Full chart of accounts, subledgers, close/reporting |
| Security | HTTPS JWT issuer/JWK/audience, signature/expiry checks, staff RBAC, restricted metrics, local password configuration | Maker-checker, tenant/customer isolation, gateway controls, pen test |
| Audit/events | Actor/request-aware audit and atomic outbox records | Independent signed audit archive; dispatcher and idempotent consumers |
| SOLID/system design | Encapsulation, constructor injection, narrow ports and architectural tests | Extract services only after ownership/event contracts are designed |
| Quality | Regression/concurrency/security/architecture tests, formatting, PostgreSQL CI | Coverage goals, load/fault tests, production snapshot upgrades |
| Operations | Non-root images, production configuration templates, probes, runbook | Managed secrets, registry, TLS gateway, monitoring/alerts, backup validation |
| Apache Fineract | Optional client HTTP adapter | Authoritative integration, ID mapping, durable commands and recovery |
| Kafka/Redis/OTel | Outbox foundation and documented boundaries | Actual broker dispatch, reference caching and telemetry export |
| Sonar/dependencies | Sonar properties, dependency update configuration | Configured server, security reports and enforced quality gates |

This is a hardened reference backend. Repository checks do not certify a regulated production deployment. See operations.md for release gates and outstanding product controls.

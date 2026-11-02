# Specification coverage

| Requirement | Current implementation |
|---|---|
| Java, Spring Boot, JPA, REST | Implemented |
| PostgreSQL | Flyway schema; H2 business tests |
| Customer/accounts | Onboarding, staff KYC decision, currency accounts |
| Savings/deposits | Deposits and withdrawals; no interest |
| Payments/collections | Internal transfers; external settlement pending |
| General ledger | Balanced transactional journal; reporting/close pending |
| Lending | Principal-only origination/disbursement/repayment |
| Apache Fineract | HTTP client foundation; authoritative integration pending |
| OAuth2/JWT/RBAC | Resource server and staff roles |
| Tests | Business integration and HTTP security tests |
| Docker/Linux | Non-root multi-stage image, PostgreSQL compose |
| CI/security | GitHub Actions verify and OWASP dependency scan |
| Monitoring | Health and Prometheus |
| Kafka, Redis, AWS | Not provisioned |
| Kubernetes | Deployment/service template; not deployed |
| External providers | Not connected |
| SonarQube | Scanner properties; server not connected |
| OpenTelemetry | Not configured |

The PDF describes a professional role, not a full product specification. This repository provides a working starting scope and records remaining work explicitly.

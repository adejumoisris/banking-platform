# Core Banking Platform

Java 17 / Spring Boot / PostgreSQL banking backend inspired by the supplied Rova role description. Educational, staff-operated reference implementation; not approved to hold real customer funds.

## Run

Install and start Docker Desktop, then run from this directory:

```powershell
Copy-Item .env.example .env
docker compose up --build
```

API: http://localhost:8080/api/v1. Swagger: http://localhost:8080/swagger-ui/index.html (browser prompts for Basic authentication). Local-only credentials: `demo` / `local-demo-only`. Health: http://localhost:8080/actuator/health.

For development with Java 17+ and Maven, supply PostgreSQL connection environment variables and run `mvn spring-boot:run -Dspring-boot.run.profiles=local`. Run tests with `mvn verify`. On this machine Java 8 is the default; use `$env:JAVA_HOME = 'C:/Users/Idris.Adejumo/.jdks/ms-17.0.18'` in your PowerShell session before invoking Maven.

Docker uses a local profile with a demo staff user. Without this profile the API validates JWTs from `JWT_ISSUER_URI`; tokens need a `roles` array containing OPERATIONS, COMPLIANCE, TELLER, CREDIT or AUDITOR. Provision an external OAuth2/OIDC provider. These endpoints are staff-facing; customer ownership and tenant isolation are not implemented.

## Example workflow

Use JSON Content-Type and local credentials. `scripts/demo.ps1` runs onboarding, deposit, ledger inspection and a loan cycle.

1. POST `/api/v1/customers` with `{"fullName":"Ada Example","email":"ada@example.com"}`.
2. POST `/api/v1/customers/{id}/verify` to record an authorized compliance decision.
3. POST `/api/v1/accounts` with `{"customerId":"<id>","currency":"NGN"}`.
4. POST `/api/v1/accounts/{id}/deposits` with `{"amount":1000.00}` and `Idempotency-Key: deposit-001`.
5. Create a second verified customer/account, then POST `/api/v1/transfers` with sourceId, destinationId and amount, supplying a unique Idempotency-Key.
6. GET `/api/v1/postings/{id}/ledger` to inspect debit and credit.
7. POST `/api/v1/loans` with accountId and principal; POST `/api/v1/loans/{id}/disburse`; POST `/api/v1/loans/{id}/repayments` with amount and Idempotency-Key.

Use a new key per business operation and the same key on retries. Amounts use exact decimals with two fractional digits. Transfers lock accounts in UUID order; balances and balanced journal entries commit in one database transaction. The database prevents negative balances and duplicate posting keys. Conflicting keys on different account sets may return 409; retry only the original operation.

## Apache Fineract boundary

`FineractGateway` provides HTTP methods to read and create Fineract clients. Set FINERACT_ENABLED=true and configure its URL, tenant and credentials against a separately provisioned Fineract instance with trusted TLS. It is not called by local banking workflows. The local ledger is the sole source of truth for this demo. Fineract is not included in compose. Before production, choose Fineract as authoritative and implement durable command delivery, remote ID mapping, reconciliation and failure recovery rather than dual-writing ledgers.

## Included and outstanding

Included: onboarding, manual KYC decision, currency accounts, deposits, withdrawals, atomic internal transfers, balanced journal, idempotency, principal-only loans, staff RBAC, JWT resource server, validation, migrations, tests, Docker, OpenAPI, health, Prometheus metrics, CI and dependency scanning.

Outstanding: automated KYC/AML/sanctions, maker-checker approval, immutable staff audit trail, settlement/reconciliation, interest/accrual/amortization, delinquency/fees, underwriting, external payment gateways/credit bureaus/notifications, Kafka/outbox, Redis, Kubernetes/AWS, OpenTelemetry export, SonarQube, rate limits, secrets management, JWT audience checks, load/penetration testing, backup recovery and regulatory approval. KYC here records a staff decision and does not verify identity. Deposits are teller postings and do not collect external funds. No FX conversion.

H2 tests exercise business rules; PostgreSQL concurrency and migration validation are required before deployment. See docs/architecture.md and docs/requirements.md. Import docs/banking.postman_collection.json into Postman and populate ID variables from responses. Keep a fixed key when testing retries; the collection generates a new key for each send. A Kubernetes template and SonarQube scanner properties are included but need your registry, secrets, cluster and server configuration.

## Upstream references

- Spring Boot requirements: https://docs.spring.io/spring-boot/3.5/system-requirements.html
- Springdoc compatibility: https://springdoc.org/v2/
- Apache Fineract API and deployment: https://github.com/apache/fineract and https://fineract.apache.org/docs/stable/

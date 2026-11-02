# Architecture

A modular monolith keeps account changes, payment postings and journal entries in one database transaction. Extract services only with explicit ownership and durable messaging. Controllers validate input and staff permissions; services enforce banking invariants; PostgreSQL stores the ledger. No network call occurs inside a monetary transaction.

Customer -> verified customer -> active currency account. A transfer debits one deposit liability and credits another. A deposit debits cash clearing and credits customer deposits. Loan disbursement debits receivables and credits deposits; repayment reverses it. Every posting has two equal journal entries in one currency. The journal is append-only through the API; production needs database permissions and audit controls to enforce immutability beyond the application.

Loans are principal-only, with PENDING -> ACTIVE -> CLOSED states. Disbursement retries do not double fund. Repayment keys are scoped to loan ID. Account rows lock in deterministic order; loan operations lock the loan before account rows.

Production: OAuth2 resource server validating JWTs against issuer discovery. Local: isolated profile with demo user. Metrics and API docs require authentication. All business endpoints are staff-operated, with role-based permissions.

Integration roadmap: outbox in the same transaction as business changes, stable event IDs, Kafka and idempotent consumers, external settlement reconciliation. Never automatically retry external financial writes without provider-supported idempotency. Store remote references, redact personal data in logs, store secrets externally.

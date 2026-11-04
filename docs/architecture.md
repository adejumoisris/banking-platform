# System design

## Boundaries and dependency direction

Packages are organized by layer. Controllers own HTTP routes and authorization; dto contains request/response records, so JPA entities are never serialized directly. Services implement banking use cases and transactions through repository interfaces. Infrastructure implements persistence and audit ports and the Fineract gateway. Model contains the JPA entities and their financial behavior. Domain contains framework-independent money, posting policies and business exceptions. Config contains application and security wiring; filter contains request correlation; exception contains HTTP exception handling. Audit contains the actor and activity contracts.

SOLID: single-responsibility use cases; explicit policies for money/posting shapes; replaceable repository and actor/audit adapters; narrow repository interfaces; constructor injection at infrastructure boundaries. Adding interfaces solely to mirror every concrete service is avoided. ArchUnit prevents services depending on HTTP or concrete persistence adapters and enforces private entity fields.


## Source layout

```text
src/main/java/com/rova/banking/
|-- BankingApplication.java
|-- controller/                 # HTTP endpoints
|-- service/                    # Banking use cases and transactions
|-- repository/                 # Persistence interfaces
|-- model/                      # Account, Customer, Loan, Posting, LedgerEntry
|-- dto/                        # Requests and response views
|-- domain/                     # Money, policies and business exceptions
|-- integration/                # Remote service ports and sanitized failures
|-- config/                     # Spring configuration and security
|-- exception/                  # HTTP exception handling
|-- filter/                     # Request correlation
|-- audit/                      # Actor and activity contracts
`-- infrastructure/
    |-- persistence/            # JPA repository implementations
    |-- audit/                  # Security and JDBC audit implementations
    `-- fineract/               # External core banking gateway
```

Dependencies flow from controllers to services, then repository contracts and models. Infrastructure implements the contracts. Services do not depend on HTTP, DTOs or infrastructure adapters. Keep new classes in the matching layer and cover new dependencies with ArchitectureTest.

Optional Fineract provisioning uses a durable command table and scoped local/remote mappings. HTTP runs outside local transactions, and uncertain creates recover by external-ID lookup. The local ledger remains authoritative. See [ADR 0003](adr/0003-fineract-provisioning.md) and [the sandbox guide](fineract-setup.md).

## Consistency and lock order

Use PostgreSQL READ COMMITTED. Acquire the loan row first for lending commands, then account rows in UUID order for posting. Refresh under lock so earlier unlocked reads cannot overwrite concurrent committed balances. Repository adapters flush existing transaction work before refresh to preserve changes made earlier in the same transaction. Transactions have bounded timeouts; production PostgreSQL also sets lock_timeout and statement_timeout. Do not retry only part of a transaction.

Every transfer updates balances and appends one posting, a debit, a credit, an audit record and an outbox event. All use the same Spring-managed database transaction. Any failure rolls everything back. No external HTTP request runs while these financial locks are held. PostgreSQL deferred constraint triggers check a complete journal at commit, allowing posting and entries to be inserted in separate statements. Journal/posting/audit UPDATE and DELETE operations are blocked; runtime permissions additionally deny them.

Customer deposits are liabilities: debit reduces a deposit account, credit increases it. Deposits debit cash clearing; withdrawals credit cash clearing. Principal disbursement debits loan receivables and credits deposits; repayment reverses it. Loan receivables currently use an aggregate ledger account; a full chart of accounts, per-loan subledger, close/reconciliation and financial reporting remain product work.

## Idempotency and API behavior

Idempotency keys have a unique database constraint. Requests sharing accounts serialize before checking the key; a replay must match account IDs, operation and amount. Different requests sharing a key return 409. Keys reserved for lending cannot be supplied to teller/transfer routes. Lending repayment keys are scoped by loan ID. Loan disbursement is state-based and cannot fund twice. Customer/account/loan creation does not yet accept idempotency keys; clients must reconcile an uncertain creation response rather than blindly retry it.

Money is exact decimal with two places for the explicitly supported NGN/USD/GBP/EUR currencies. No silent rounding, overdrafts, unsupported FX or balance overflow. Pending loans have zero outstanding; activation recognizes principal, repayments reduce it and zero closes the loan. Lending is principal-only.

## Scale and failure recovery

Scale stateless API replicas behind a TLS gateway. Connection pool size is part of the database connection budget: replicas multiplied by pool size, plus migration, monitoring and worker connections. Hot accounts serialize intentionally; partition operational settlement accounts and assess throughput with workload-specific tests before extraction. Cache reference data only; cached balances must never authorize spending.

The outbox persists schema-versioned events with stable IDs in the financial transaction. A future dispatcher must claim batches using SKIP LOCKED, publish with bounded retries, and mark delivery only after acknowledgement. This provides at-least-once delivery; consumers must deduplicate event IDs. A crash after broker acknowledgement but before marking delivery can produce duplicates. Dispatch is not implemented yet. Do not claim exactly-once external settlement.

Keep Fineract/payment-provider writes outside ledger transactions. Choose one authoritative core, map remote IDs, send idempotent durable commands and reconcile provider statements. Timeouts after remote acceptance are ambiguous and require status lookup, not a new payment key.

## Security and audit

Staff roles protect every banking route. Production JWTs require trusted HTTPS issuer/JWK configuration and expected audience, signature and lifetime. Local authentication is explicit and isolated. Metrics require MONITORING; API docs are disabled by default. Request IDs are bounded and sanitized before use in response headers, logs and audit records. Audit and event payloads contain identifiers and action metadata rather than customer names or email addresses.

This is single-bank staff access, without customer ownership or tenant isolation. KYC approval is a recorded compliance decision rather than automated verification. Maker-checker duties, suspicious-activity controls, privileged-access management and independent tamper-evident audit exports are deployment prerequisites where required.

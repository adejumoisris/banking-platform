# ADR 0003: local ledger authority and durable Fineract provisioning

Status: accepted for this integration phase.

The local PostgreSQL ledger remains the authority for balances, payments and principal-only lending, consistent with ADR 0001. Fineract is an optional downstream provisioning target. Staff explicitly request creation of a verified client and a savings application; the integration never sends deposits, withdrawals, transfers, loan disbursements, repayment or automatic account activation to Fineract. A Fineract savings application is not a second source of truth for the local balance.

A migration making Fineract the financial core is separate work: it must replace local financial command execution, define product/loan semantics, migrate opening positions and reconcile transactions. It must not be implemented by writing the same payment independently to both ledgers.

Provisioning commands snapshot their payload in the queue transaction, alongside actor audit and an outbox record. Mappings and command state live in fineract_command; uniqueness covers target scope, resource kind, local ID, external ID and remote ID. Scope hashes the configured endpoint and tenant. Changing either creates a different namespace; operators must reconcile any endpoint rename or tenant move before provisioning again.

Dispatch runs outside any ambient local transaction. Workers atomically acquire 90-second leases with unique tokens, and all updates use the token to fence stale workers. A persisted create_attempted marker commits before a remote POST. Every dispatch first looks up the stable external ID. Once a create might have been sent, recovery is lookup-only: a missing remote resource never authorizes another POST. This favors avoiding duplicates over automatic progress after an ambiguous outcome. A crash between marking intent and sending the POST can therefore require operator review.

Reads and transient failures retry with capped exponential delay. Eight attempts move the command to REVIEW; ordinary rejected requests become FAILED. Authorized reconciliation requeues REVIEW commands without clearing create_attempted, so it cannot turn an ambiguous operation into a blind duplicate write. Remote identity/product/currency mismatches are not silently mapped. There is no public reset-create flag. Resolving a genuinely missing resource after ambiguous creation requires a reviewed operational procedure.

Payloads contain customer names and must be handled as personal data. API views expose sanitized status and identifiers, never payloads, credentials or lease tokens. Errors persist sanitized codes, not provider response bodies. Credentials and TLS trust are configured externally; remote plain HTTP is rejected. The background worker is opt-in. Integration API routes exist only when enabled and enforce staff roles.

Validation combines repository/state-machine and authorization tests, HTTP request/response contract tests, and the full PostgreSQL suite. Contract tests are not a substitute for running scripts/demo-fineract.ps1 against the chosen Fineract version and configured office/product.

# ADR 0001: transactional modular monolith

Status: accepted.

Customer onboarding, account management, payments, lending and ledger queries are separate application services. Domain objects own financial invariants. Services depend on narrow repository ports; a JPA adapter owns SQL and locking. HTTP DTOs isolate public contracts from entities. Clock and actor context are injectable dependencies.

Keep balance changes, balanced journal entries, audit records and outbox events inside a single PostgreSQL transaction. A failed write rolls back the whole operation. Lock order is loan (when applicable), then accounts sorted by UUID. Never call a remote provider while these locks are held. Configure bounded lock and transaction timeouts; retry an entire transaction with the original idempotency key after a transient conflict.

This deliberately avoids distributed transactions and premature service extraction. Separate deployment units require explicit data ownership, idempotent messages, durable delivery and reconciliation first. The local ledger remains authoritative; enabling a Fineract HTTP client does not make this a Fineract-backed core.

SOLID application: one use-case responsibility per service; domain policies are independent of HTTP; repository and audit ports have focused contracts; constructor injection permits adapter substitution. Add interfaces at genuine substitution boundaries rather than one interface per class.

# ADR 0002: atomic audit and outbox

Status: accepted.

ActivityRecorder is an application port implemented by a JDBC adapter sharing the JPA transaction. It writes an actor/action/request audit row and schema-versioned outbox envelope, without customer PII. Delivery is not a prerequisite for committing a local ledger change; intent must persist atomically. There is no dispatcher in this release.

PostgreSQL blocks posting, journal and audit UPDATE/DELETE and validates journal pairs at commit with deferred constraint triggers. Restrict application database permissions separately; the database owner can still disable triggers or mutate privileged state. These controls do not provide cryptographic tamper evidence against an administrator.

Future publishers must use stable event IDs, at-least-once delivery, bounded retries, dead-letter/operational review and idempotent consumers. Financial systems must reconcile their own source of truth and external settlement independently of broker delivery.

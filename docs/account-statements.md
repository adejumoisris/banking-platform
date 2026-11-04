# Transaction history and account statements

Staff with OPERATIONS or AUDITOR roles can read these endpoints. Anonymous requests return 401; other staff roles return 403. All successful responses use Cache-Control: no-store. These endpoints read the local ledger, including loan disbursements and repayments; Fineract provisioning does not create statement entries.

| Endpoint under /api/v1/accounts/{id} | Behavior |
|---|---|
| GET /transactions | Paginated history, newest first; optional from/to filters |
| GET /statement | Opening/closing balances and full-period totals with chronological transactions; from/to required |
| GET /statement.csv | Complete bounded-period CSV attachment; from/to required |

## Dates and pagination

Use ISO 8601 timestamps with a UTC Z suffix or explicit offset. from is inclusive; to is exclusive. For all of November 2026 use from=2026-11-01T00:00:00Z and to=2026-12-01T00:00:00Z. from must precede to. Finer-than-microsecond query thresholds are rounded upward internally, preserving the inclusive/exclusive semantics against microsecond-precision stored timestamps. Filters accept years 0001 through 9999. History can omit either bound or both; JSON statements and CSV require both.

JSON page numbers start at zero. Defaults: page=0 and size=50. Allowed page: 0..100000; size: 1..100. Responses include totalElements and totalPages. A page beyond the end has no transactions but retains the full-period statement totals. An empty account has zero balances and zero pages; an empty period carries forward any prior balance. Unknown accounts return 404; invalid filters/pagination and missing statement dates return 400.

```text
GET /api/v1/accounts/{id}/transactions?page=0&size=25
GET /api/v1/accounts/{id}/statement?from=2026-11-01T00:00:00Z&to=2026-12-01T00:00:00Z&page=0&size=50
GET /api/v1/accounts/{id}/statement.csv?from=2026-11-01T00:00:00Z&to=2026-12-01T00:00:00Z
```

Requests in docs/banking.postman_collection.json use accountId, statementFrom and statementTo variables. Set collection credentials locally. Swagger exposes the same routes.

## Financial meaning

Each row represents one CUSTOMER:{accountId} journal leg joined to its immutable posting. A credit to the customer liability account is moneyIn; a debit is moneyOut. Transfers show the other local account as counterpartyAccountId. Cash and lending legs have no counterparty account. reference is the posting UUID, not the internal idempotency key; names, email addresses and provider credentials are not included.

Opening balance is all earlier customer-ledger credits minus debits. Period totals sum only entries in [from,to). Closing balance equals openingBalance + totalMoneyIn - totalMoneyOut. Historical closing balance may differ from the account's current balance when later transactions exist. Totals cover the complete period, regardless of page size.

Each response reads the account, totals and rows within one read-only PostgreSQL REPEATABLE READ transaction. A concurrent posting cannot appear in its rows without appearing in its totals. Ordering uses posting timestamp, posting UUID and ledger-entry UUID, reversing all three for newest-first history. Equal timestamps are deterministic but do not claim an otherwise-unrecorded intra-timestamp financial order; no per-transaction running balance is invented. Separate pagination requests are separate database snapshots and can change as new transactions commit. There is no cross-request snapshot token.

V5 adds an index on ledger account/posting and one on posting timestamp/ID. Existing financial rows and balances are not modified.

## CSV format and limits

The response is UTF-8 text/csv with an attachment filename account-{id}-statement.csv, CRLF line endings and quoted fields. Every row has the same ten columns:

```text
record_type,account_id,currency,occurred_at_utc,reference,type,counterparty_account_id,money_in,money_out,balance
```

An OPENING_BALANCE row contains the inclusive start timestamp and opening balance. TRANSACTION rows contain chronological references and money in/out; their balance field is empty. A CLOSING_BALANCE row contains the exclusive end timestamp, period money-in/out totals and closing balance. Empty periods still include opening and closing rows. Only controlled IDs, enums, timestamps and plain decimal amounts are exported, with no customer-supplied free text or internal request keys.

CSV includes the entire requested period, without page parameters. It supports up to 10,000 transactions per request; larger exports return 400 and require a narrower date range. Export never silently truncates. JSON remains paginated for larger histories.

## Verification

AccountStatementTest covers account isolation, transfer direction, idempotent retries, inclusive/exclusive boundaries, historical versus current balances, empty ranges, deterministic pagination ties, lending entries, CSV completeness/limits, authorization and invalid inputs. A PostgreSQL-only concurrent-posting test proves rows and totals use the same snapshot. H2 skips that concurrency check.

With the sandbox running, verify the real HTTP routes and save an actual CSV:

```powershell
./scripts/demo-statements.ps1 -Password $env:LOCAL_DEMO_PASSWORD
```

The demo creates synthetic banking fixtures, asserts NGN 1,200 money in, NGN 450 money out and a NGN 750 closing balance, checks pagination and recipient isolation, then downloads/parses .sandbox/fineract/account-statement.csv. It does not delete financial data.

## Verified locally — 4 November 2026

The complete Maven verification passed against PostgreSQL 17: 74 tests, zero failures, errors or skips. The focused H2 statement suite also passed (with its PostgreSQL-only snapshot check skipped), and the added fractional-timestamp regression passed on both database engines.

The running sandbox passed scripts/demo-statements.ps1: four account transactions, NGN 0 opening balance, NGN 1,200 money in, NGN 450 money out and NGN 750 closing balance. The recipient statement showed NGN 250. The downloaded CSV was parsed and checked against those JSON totals, and all three routes were present in Swagger. Both APIs remained healthy with Fineract background dispatch enabled.

The latest local sample is .sandbox/fineract/account-statement.csv; the structured report is .sandbox/fineract/statements-result.json. These synthetic-data artifacts are ignored by Git. Existing account data was retained; V5 only added query indexes.

# Staff dashboard

The staff workspace is served by Spring Boot at http://localhost:8080/ and bundled in the JAR and Docker image. No separate frontend server is needed.

## Sign in

For the local Fineract sandbox use username demo and LOCAL_DEMO_PASSWORD from the ignored .env.fineract-sandbox file. Other local installations use their configured password. Never commit or share this file.

Outside the local profile the screen accepts a staff access token from the configured identity provider. An OIDC redirect login flow is not implemented. Use HTTPS outside local development.

Authorization stays in memory in the current tab and is cleared on sign-out. It is never written to localStorage, sessionStorage, cookies or URLs. Reloading requires signing in again. The backend enforces roles independently of visible buttons.

## Workflows

- Overview: actual customer/account/loan counts, balances separated by currency, seven days of UTC transaction counts and recent postings.
- Customers: paginated name/email/ID search, details, creation and KYC verification.
- Accounts: paginated customer/ID search, balances, details and account opening for verified customers.
- Transactions: teller deposits/withdrawals and Operations transfers. Every write has a review step. Exact decimal strings preserve precision. Payment retries reuse their original idempotency key.
- Loans: Credit/Auditor browsing, Credit origination/disbursement and Operations repayments. Loans remain principal-only.
- Statements: account ID and inclusive calendar dates in the browser timezone, paginated transactions and authenticated CSV download.
- Fineract: scoped command search/details, provisioning, dispatch and reconciliation of REVIEW commands. Failed commands are not reset automatically.

Detail dialogs and receipts show full IDs to copy into forms. Directories contain 20 records per page. Compliance verifies KYC; Tellers deposit/withdraw; Operations transfer/repay; Credit originates/disburses; Auditors read. Monitoring-only staff have no dashboard modules.

An interrupted response does not prove a write failed. Payments can be retried with the same key in the current tab. For uncertain customer/account/loan creation, check the directory before submitting again: these creation APIs have no idempotency key.

## Added read APIs

Protected read responses use Cache-Control: no-store.

| Route | Access |
| --- | --- |
| GET /api/v1/ui/config | Public configuration; no credentials |
| GET /api/v1/session | Authenticated username and roles |
| GET /api/v1/dashboard | Operations, Auditor |
| GET /api/v1/customers and /{id} | Operations, Compliance, Auditor |
| GET /api/v1/accounts | Operations, Auditor |
| GET /api/v1/loans | Credit, Auditor |
| GET /api/v1/integrations/fineract/commands | Operations, Auditor |

Directories accept q, zero-based page, size (1–100), and return items/page/size/totalElements/totalPages. Customers accept status; accounts customerId/currency/status; loans accountId/status; Fineract commands status. Search wildcards are literal. Fineract views omit payloads, leases and target scopes.

HTML/assets have a restrictive content security policy and no external scripts or fonts. Cross-origin browser writes are rejected; authenticated CLI clients remain supported. A TLS reverse proxy must use correctly trusted forwarded scheme/host headers so origin checks match the external staff address.

## Verification

Node.js 20+ is needed only for development checks:

    npm ci
    npx playwright install chromium --only-shell
    npm run test:ui:unit
    npm run test:ui

Playwright starts and stops a loopback static preview on port 4173 with mocked APIs. It checks sign-in, directories, record creation, transfer review/retry safety, statements/CSV, Auditor restrictions, session expiry, CSP charts and mobile navigation. Backend tests separately check real PostgreSQL queries, security and exact amounts.

Optional read-only live sandbox check:

    node scripts/smoke-staff-ui.mjs

This reads local credentials without printing them, uses an isolated browser, visits modules and downloads a statement if an account exists. It does not create records or post money.

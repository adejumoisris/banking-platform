# Live Fineract verification — 4 November 2026

The local sandbox was commissioned against the official Apache Fineract image pinned in compose.fineract-sandbox.yaml (upstream commit fee1da55c2dc93cd2531624ab7fc8e81d8f56cb6). Both services were left running on loopback, with the background provisioning worker enabled.

| Check | Result |
|---|---|
| Complete Maven verification against isolated PostgreSQL | 54 tests, zero failures/errors/skips |
| Banking HTTP flow | PASS: onboarding, KYC, accounts, deposit/transfer/replay, lending and repayment |
| Banking final balances | NGN 750 sender, NGN 250 recipient; loan CLOSED, zero outstanding |
| Live manual provisioning | PASS: Fineract client 2 and savings application 1; stable mapping replays |
| Live background provisioning | PASS: Fineract client 3 and savings application 2; no manual dispatch calls |
| Direct provider lookups | IDs, client ownership and NGN currency matched local mappings |
| Local balance after provisioning | Zero; no remote financial transactions submitted |
| Integration user access to user administration | HTTP 403, as expected |
| Original seeded administrator password | HTTP 401, as expected |
| Rotated administrator and NGN product access | HTTP 200 |

Tenant: default. Office: 1. NGN savings product: 1 (Rova NGN Sandbox). The integration role includes ACTIVATE_CLIENT because the client creation request uses active=true; it has no savings transaction or savings activation permissions.

Live commissioning exposed the required person legalFormId=1 field and a separate ACTIVATE_CLIENT permission check. The payload and regression coverage were corrected. Initial synthetic validation/authorization failures remain as historical FAILED commands; they were not reset or blindly replayed. Subsequent manual and background flows both passed.

The instance is a synthetic-data development sandbox with a pinned development image, not a production deployment. The local ledger remains authoritative. Savings applications are provisioned in Fineract without activating them or copying local balances.

Local JSON evidence is under ignored .sandbox/fineract/: banking-result.json, manual-result.json, worker-result.json and security-result.json. Credentials are exclusively in ignored .env.fineract-sandbox and must stay with the existing database volumes. See fineract-setup.md for start/stop and recovery instructions.

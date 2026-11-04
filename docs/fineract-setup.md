# Fineract sandbox and integration

## Current status

The local ledger owns money. The integration provisions verified clients and savings applications in Fineract, records mappings and supports durable retry/reconciliation. It does not mirror local financial transactions or activate remote savings accounts. See ADR 0003. The optional local sandbox uses a pinned official Fineract image and isolated PostgreSQL containers. Automated HTTP contract tests remain available independently of the live sandbox.

Live commissioning results are recorded in [the verification report](fineract-live-verification.md). The current customer model provisions natural persons with legalFormId=1; business entities require an explicit domain/API extension.

## Reproducible local sandbox

With Docker running and Java 17+ installed, start and verify the complete sandbox from the repository root:

```powershell
./scripts/start-fineract-sandbox.ps1 -JavaHome 'C:\tools\jdk-17.0.6+10'
```

Use your actual JDK path. The startup script generates local credentials in the Git-ignored `.env.fineract-sandbox`, starts `compose.fineract-sandbox.yaml`, initializes the default tenant, enables NGN, creates the zero-interest `Rova NGN Sandbox` product and a provisioning-only integration role/user, and rotates the seeded administrator password. It then starts the banking JAR and runs the banking and live Fineract demos. The initial migration can take several minutes. `-SkipBuild` reuses an existing packaged JAR; `-SkipDemos` restarts without creating additional demo fixtures.

The Fineract image is pinned to digest `sha256:c7b24478c75f97aceb4385b3b2533e1879634b7ff8c2f0be4a80f05354604857`, built from upstream commit `fee1da55c2dc93cd2531624ab7fc8e81d8f56cb6`. This is the official development image verified for this local sandbox, not a production release or support commitment. PostgreSQL 18.3 holds Fineract's databases; PostgreSQL 17 holds the local banking database.

| Component | Local address |
|---|---|
| Banking API | http://127.0.0.1:8080/api/v1 |
| Banking Swagger UI | http://127.0.0.1:8080/swagger-ui/index.html |
| Fineract API | http://127.0.0.1:8443/fineract-provider/api/v1 |
| Banking PostgreSQL | 127.0.0.1:55432 |

The sandbox deliberately uses HTTP on loopback rather than a self-signed HTTPS bypass. Ports are published only to 127.0.0.1; Fineract's database and debug interfaces are not published. The native banking API is bound to loopback and uses the same validated HTTP loopback exception as the adapter. Docker containers may communicate on their dedicated sandbox network. This setup is for local synthetic-data development only.

The banking username is `demo`; its generated password is `LOCAL_DEMO_PASSWORD` in `.env.fineract-sandbox`. Fineract uses `rova-integration` with `FINERACT_PASSWORD`; the sandbox administrator is `mifos` with the rotated `FINERACT_ADMIN_PASSWORD`. Do not commit or paste the credential file. The integration role grants only READ_CLIENT, CREATE_CLIENT, ACTIVATE_CLIENT, READ_SAVINGSACCOUNT, CREATE_SAVINGSACCOUNT and READ_SAVINGSPRODUCT.

Logs, API process metadata and JSON demo reports live under ignored `.sandbox/fineract/`, so `mvn clean` does not remove running-process metadata or sandbox credentials. The startup script only stops a prior API process if its recorded process start time and JAR match. Database volumes survive restarts. Failed startup leaves its containers and logs available for diagnosis.

For background dispatch, set `FINERACT_WORKER_ENABLED=true` in `.env.fineract-sandbox` and rerun startup with `-SkipBuild -SkipDemos`. The live demo then verifies background dispatch by polling command status without calling the dispatch endpoint. You can also run `scripts/demo-fineract.ps1 -UseWorker` directly. Manual dispatch remains available for troubleshooting.

Stop without deleting data:

```powershell
./scripts/stop-fineract-sandbox.ps1
```

This stops only the recorded banking API and this Compose project's containers, retaining the volumes and credentials. Rerun the start command to resume. Keep the credential file with the existing volumes: regenerating passwords while retaining initialized database volumes will not update database passwords.

## Get a test instance

Docker Desktop must be running with its WSL 2 Linux engine on Windows. In an Administrator PowerShell, install WSL with `wsl --install`, complete any required restart and Linux setup, then enable Docker Desktop's WSL 2 engine. This is a machine setup step, not something the verification script performs. Follow [Microsoft's WSL installation guide](https://learn.microsoft.com/windows/wsl/install) and [Docker's WSL requirements](https://docs.docker.com/desktop/features/wsl/).

Alternatively, use a separate Linux VM with Docker. Keep sandbox access private; use synthetic customer data. You do not need a public shared demo or production account.

Use the official [Apache Fineract source and prerequisites](https://github.com/apache/fineract) and [integration setup documentation](https://fineract.apache.org/docs/stable/). In a separate folder from banking-platform:

```powershell
git clone https://github.com/apache/fineract.git fineract-sandbox
Set-Location fineract-sandbox
# Select a released tag and install the JDK required by that release's README.
# Build the local image expected by upstream Compose:
./gradlew.bat :fineract-provider:jibDockerBuild '-Djib.to.image=fineract' -x test -x cucumber
docker compose up -d
```

The upstream Compose files change by release; inspect the checked-out README and resolved `docker compose config` before starting. The default upstream service is normally HTTPS on 8443 with API base `https://localhost:8443/fineract-provider/api/v1`. The image build may require a newer JDK than this backend's Java 17. Use a dedicated shell for Fineract so this project's JAVA_HOME remains correct. Do not expose the upstream database or debug port to a public network.

Wait for `/fineract-provider/actuator/health` to report UP. Configure a sandbox integration user using the authentication instructions for that release and change any seeded password. Grant only the office, client, savings product and savings application access needed. Record the tenant, office ID and an NGN savings product ID from that instance. The product must actually use NGN; a product ID alone does not establish currency.

The Java adapter verifies HTTPS certificates. If the sandbox uses a self-signed certificate, import that certificate into a dedicated local Java truststore and launch the banking API with `-Djavax.net.ssl.trustStore=<absolute-path>` and its truststore password. Keep local truststore artifacts under ignored target/. Browser certificate acceptance does not configure Java trust. Never disable certificate verification. An HTTP sandbox is accepted only at localhost or 127.0.0.1.

## Configure the banking API

Set these variables in the shell that launches banking-platform. Replace the credentials and product ID with your sandbox values; do not commit them:

```powershell
$env:FINERACT_ENABLED = 'true'
$env:FINERACT_URL = 'https://localhost:8443/fineract-provider/api/v1'
$env:FINERACT_TENANT = 'default'
$env:FINERACT_USERNAME = '<sandbox-integration-user>'
$env:FINERACT_PASSWORD = '<sandbox-password>'
$env:FINERACT_OFFICE_ID = '1'
$env:FINERACT_NGN_PRODUCT_ID = '<actual-NGN-savings-product-id>'
$env:FINERACT_WORKER_ENABLED = 'false'
```

Zero/unset product IDs mean that currency has no configured product. Optional USD, GBP and EUR mappings use FINERACT_USD_PRODUCT_ID, FINERACT_GBP_PRODUCT_ID and FINERACT_EUR_PRODUCT_ID. Keep the worker off for the first manual run. Start the banking API with its normal isolated PostgreSQL database and local profile. Native Spring Boot does not load the repository .env file automatically; set process environment variables or use Compose.

When the banking API runs in Docker, the Fineract URL must resolve from inside its container. Use a shared-network service name or a reachable host address and a certificate valid for that name; localhost inside the API container refers to the API container itself.

## Verify provisioning

With both servers running, Java trust configured and LOCAL_DEMO_PASSWORD set for the banking API:

```powershell
./scripts/demo-fineract.ps1 -BaseUrl 'http://localhost:8080'
```

The script creates a new verified local customer and an NGN account, provisions the remote client, submits a savings application and checks replayed requests reuse both mappings. The local balance must stay zero. It creates synthetic fixtures in both systems and does not delete them. This is the live sandbox check; it cannot pass until a Fineract instance, credentials, office and savings product are available.

The integration uses Fineract's `clients/external-id/{externalId}`, `savingsaccounts/external-id/{externalId}`, `savingsproducts/{id}`, `POST clients` and `POST savingsaccounts` contracts. Verify support against your chosen release. Additional tenant policies, mandatory client fields, business dates or product settings may require a reviewed payload extension; contract tests do not certify those deployment-specific policies.

## Operations

All paths below are under `/api/v1/integrations/fineract`:

| Method and path | Purpose | Roles |
|---|---|---|
| POST /customers/{localCustomerId} | Queue verified client provisioning | OPERATIONS |
| POST /accounts/{localAccountId} | Queue savings application after client mapping succeeds | OPERATIONS |
| GET /commands/{commandId} | Read sanitized state and mapping | OPERATIONS, AUDITOR |
| POST /commands/{commandId}/dispatch | Attempt an eligible command | OPERATIONS |
| POST /commands/{commandId}/reconcile | Requeue REVIEW for another lookup cycle | OPERATIONS |

Enable FINERACT_WORKER_ENABLED=true after the manual smoke test. Polling defaults to 5 seconds; each cycle selects up to 10 ready commands. Leases last 90 seconds, reads/connects have bounded timeouts and retry delay caps at 300 seconds. Never run remote HTTP within a local financial transaction.

RETRY is transient; REVIEW requires inspection; FAILED indicates a non-retryable rejection. Persisted error codes omit response bodies and personal data. Reconcile an ambiguous operation by inspecting its external ID in the same Fineract endpoint/tenant. Reconciliation never clears the create marker or automatically sends another uncertain POST. There is intentionally no retry-with-new-ID shortcut. Endpoint/tenant changes require a mapping migration plan. Apply V4 before deploying; grant the runtime role SELECT/INSERT/UPDATE on fineract_command (see deploy/database-roles.sql).

## Automated checks

FineractServiceTest verifies committed intents, mapping replay, transaction rollback, lookup-only recovery, exhausted retries, stale-worker fencing, currency mapping and API roles. FineractGatewayTest verifies tenant/auth headers, external-ID routes, request filtering, identity checks and permanent error handling using a mock HTTP server. Run the complete PostgreSQL verification runner for persistence coverage; a live provider smoke test remains a separate gate.

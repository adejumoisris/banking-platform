# Verification

`mvn spotless:apply` formats Java. `mvn verify` checks the format, Java/Maven versions, domain rules, architectural boundaries, signed JWT validation, staff permissions, transaction rollback and concurrency behavior.

Default database: H2 PostgreSQL compatibility mode, portable Flyway migrations and Hibernate schema validation. PostgreSQL-only integrity and statement-snapshot tests are deliberately skipped with H2. CI must run the PostgreSQL job successfully before merging or releasing.

To run all tests against an isolated PostgreSQL 17 database, configure these PowerShell variables (never target a customer database):

```powershell
$env:TEST_DATABASE_URL = 'jdbc:postgresql://localhost:55432/banking_test'
$env:TEST_DATABASE_DRIVER = 'org.postgresql.Driver'
$env:TEST_DATABASE_USER = 'banking_test'
$env:TEST_DATABASE_PASSWORD = '<test-only-password>'
$env:TEST_FLYWAY_LOCATIONS = 'classpath:db/migration,classpath:db/postgresql'
mvn verify
```

The suite creates test customers, accounts, postings and migrations and leaves committed test fixtures in this isolated database. PostgreSQL tests verify rejected unbalanced commits and blocked mutation of journals/postings/audit. Concurrency tests cover overdraft prevention, stale unlocked reads and transfer rollback. Audit tests prove that state, journal, audit and outbox roll back together.

On constrained Windows machines, use `scripts/verify.ps1`; it limits JVM memory and disables a separate test fork. CI uses normal forked tests. Mockito uses subclass mocking to avoid JVM agent self-attachment. Full load, failover, migration upgrade from a production snapshot, security scanning, restore and provider sandbox testing are separate release gates.
## Isolated PostgreSQL verification and HTTP demo on Windows

With Java 17+, Maven 3.9+ and portable PostgreSQL 17 binaries available, run:

```powershell
./scripts/verify-postgres.ps1 -PostgresBin 'C:\path\to\pgsql\bin'
```

Portable Windows binaries are available from [EDB's PostgreSQL binaries page](https://www.enterprisedb.com/download-postgresql-binaries). The runner does not install services or require Docker/WSL. It initializes a fresh password-protected cluster under `target/postgres-verification/<run-id>`, binds PostgreSQL and the API to loopback on dynamically selected ports, and creates separate `banking_test` and `banking_demo` databases. It runs Maven verification and requires zero failing or skipped tests, including the PostgreSQL integrity tests, before launching the packaged API and exercising the demo. Do not run `mvn clean` while this runner is active because its temporary database lives under `target`.

The runner stops its API and database in `finally` and restores process environment variables. Logs, test fixtures and `demo-result.json` remain under the run directory for inspection; they are ignored by Git. The initialization password file is deleted after initialization. This cluster uses a test-only database owner, not the restricted runtime role required for production.

To exercise an already running local API instead:

```powershell
./scripts/demo.ps1 -BaseUrl 'http://localhost:8080' -Password $env:LOCAL_DEMO_PASSWORD
```

The demo creates a fresh verified customer and two NGN accounts, deposits 1,000, transfers 250, disburses a principal-only loan of 200 and repays it. It asserts final balances of NGN 750 and NGN 250, a CLOSED loan with zero outstanding, and balanced deposit, transfer and repayment journal entries. Repeating each financial command with the original replay key (or loan ID for disbursement) must not move money twice. An assertion or HTTP failure makes the script fail. Each run creates unique fixtures; it is intended for local/test environments and does not delete financial records.

If an older Windows JDK fails to start Tomcat with `Unable to establish loopback connection` and `UnixDomainSockets.connect0`, add `-UseTcpSelectorFallback`. This optional workaround points Java's Unix socket temporary directory at a nonexistent path inside the unique run directory, causing the JDK to fall back to TCP loopback for its selector wakeup pipe. It affects only the demo JVM. Prefer a supported, updated Java runtime for normal development.

AccountStatementTest verifies historical balances, date boundaries, paginated history, CSV exports and staff permissions. With PostgreSQL it also tests a statement read while another transaction commits, proving totals and rows share a repeatable-read snapshot. See account-statements.md for the live HTTP/CSV demo.

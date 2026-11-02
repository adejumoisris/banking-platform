# Verification

`mvn spotless:apply` formats Java. `mvn verify` checks the format, Java/Maven versions, domain rules, architectural boundaries, signed JWT validation, staff permissions, transaction rollback and concurrency behavior.

Default database: H2 PostgreSQL compatibility mode, portable Flyway migrations and Hibernate schema validation. Two PostgreSQL-specific tests are deliberately skipped with H2. CI must run the PostgreSQL job successfully before merging or releasing.

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

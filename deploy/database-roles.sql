-- DBA-reviewed, one-time template for a dedicated banking database.
-- Run migrations as a separate identity that owns the schema/tables.
-- Provision the LOGIN credential through your secrets manager, never in this file.
CREATE ROLE banking_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
GRANT CONNECT ON DATABASE banking TO banking_app;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO banking_app;
GRANT SELECT, INSERT, UPDATE ON customer, bank_account, loan TO banking_app;
GRANT SELECT, INSERT ON posting, ledger_entry, audit_event TO banking_app;
GRANT SELECT, INSERT, UPDATE ON outbox_event TO banking_app;
GRANT SELECT, INSERT, UPDATE ON fineract_command TO banking_app;
ALTER ROLE banking_app SET lock_timeout = '5s';
ALTER ROLE banking_app SET statement_timeout = '15s';
-- No table ownership, DDL, DELETE or TRUNCATE privileges are granted.
-- Re-evaluate grants after every schema upgrade; never grant ALL to the runtime role.

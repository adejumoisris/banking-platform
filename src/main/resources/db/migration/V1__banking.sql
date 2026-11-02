CREATE TABLE customer (
 id UUID PRIMARY KEY, full_name VARCHAR(160) NOT NULL, email VARCHAR(254) NOT NULL UNIQUE,
 kyc_status VARCHAR(20) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE bank_account (
 id UUID PRIMARY KEY, customer_id UUID NOT NULL REFERENCES customer(id),
 currency VARCHAR(3) NOT NULL, balance NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
 status VARCHAR(20) NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_account_customer ON bank_account(customer_id);
CREATE TABLE posting (
 id UUID PRIMARY KEY, idempotency_key VARCHAR(100) NOT NULL UNIQUE,
 source_id UUID REFERENCES bank_account(id), destination_id UUID REFERENCES bank_account(id),
 amount NUMERIC(19,2) NOT NULL CHECK (amount > 0), currency VARCHAR(3) NOT NULL,
 kind VARCHAR(30) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE ledger_entry (
 id UUID PRIMARY KEY, posting_id UUID NOT NULL REFERENCES posting(id),
 ledger_account VARCHAR(80) NOT NULL, side VARCHAR(6) NOT NULL CHECK (side IN ('DEBIT','CREDIT')),
 amount NUMERIC(19,2) NOT NULL CHECK (amount > 0), currency VARCHAR(3) NOT NULL
);
CREATE INDEX idx_ledger_posting ON ledger_entry(posting_id);
CREATE TABLE loan (
 id UUID PRIMARY KEY, account_id UUID NOT NULL REFERENCES bank_account(id),
 principal NUMERIC(19,2) NOT NULL CHECK (principal > 0),
 outstanding NUMERIC(19,2) NOT NULL CHECK (outstanding >= 0), status VARCHAR(20) NOT NULL
);

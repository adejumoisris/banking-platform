ALTER TABLE customer ADD CONSTRAINT ck_customer_kyc CHECK (kyc_status IN ('PENDING','VERIFIED'));
ALTER TABLE bank_account ADD CONSTRAINT ck_account_status CHECK (status IN ('ACTIVE','FROZEN','CLOSED'));
ALTER TABLE bank_account ADD CONSTRAINT ck_account_currency CHECK (currency IN ('NGN','USD','GBP','EUR'));
ALTER TABLE posting ADD CONSTRAINT ck_posting_shape CHECK (
 (kind IN ('DEPOSIT','LOAN_DISBURSEMENT') AND source_id IS NULL AND destination_id IS NOT NULL) OR
 (kind IN ('WITHDRAWAL','LOAN_REPAYMENT') AND source_id IS NOT NULL AND destination_id IS NULL) OR
 (kind = 'TRANSFER' AND source_id IS NOT NULL AND destination_id IS NOT NULL AND source_id <> destination_id)
);
UPDATE loan SET outstanding = 0 WHERE status = 'PENDING';
ALTER TABLE loan ADD CONSTRAINT ck_loan_state CHECK (
 (status = 'PENDING' AND outstanding = 0) OR
 (status = 'ACTIVE' AND outstanding > 0 AND outstanding <= principal) OR
 (status = 'CLOSED' AND outstanding = 0)
);
CREATE TABLE audit_event (
 id UUID PRIMARY KEY, actor TEXT NOT NULL, action VARCHAR(80) NOT NULL,
 aggregate_id UUID NOT NULL, request_id VARCHAR(100), created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_audit_aggregate ON audit_event(aggregate_id, created_at);
CREATE TABLE outbox_event (
 id UUID PRIMARY KEY, aggregate_id UUID NOT NULL, event_type VARCHAR(80) NOT NULL,
 payload TEXT NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 available_at TIMESTAMP WITH TIME ZONE NOT NULL, attempts INTEGER NOT NULL CHECK (attempts >= 0),
 delivered_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_outbox_pending ON outbox_event(delivered_at, available_at, created_at);

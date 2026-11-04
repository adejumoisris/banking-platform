CREATE INDEX idx_ledger_account_posting ON ledger_entry(ledger_account, posting_id);
CREATE INDEX idx_posting_created_id ON posting(created_at, id);

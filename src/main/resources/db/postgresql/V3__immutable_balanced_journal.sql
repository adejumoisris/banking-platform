-- PostgreSQL-only guarantees; applied in addition to the portable migrations.
CREATE FUNCTION reject_financial_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION 'Posted financial and audit records are immutable' USING ERRCODE = '23514';
END;
$$;
CREATE TRIGGER immutable_posting BEFORE UPDATE OR DELETE ON posting
 FOR EACH ROW EXECUTE FUNCTION reject_financial_mutation();
CREATE TRIGGER immutable_journal BEFORE UPDATE OR DELETE ON ledger_entry
 FOR EACH ROW EXECUTE FUNCTION reject_financial_mutation();
CREATE TRIGGER immutable_audit BEFORE UPDATE OR DELETE ON audit_event
 FOR EACH ROW EXECUTE FUNCTION reject_financial_mutation();

CREATE FUNCTION assert_balanced_posting() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
 target UUID;
 p posting%ROWTYPE;
 entry_count BIGINT;
 debit_count BIGINT;
 signed_total NUMERIC;
 correct_values BOOLEAN;
BEGIN
 IF TG_TABLE_NAME = 'posting' THEN target := NEW.id; ELSE target := NEW.posting_id; END IF;
 SELECT * INTO STRICT p FROM posting WHERE id = target;
 SELECT count(*), count(*) FILTER (WHERE side = 'DEBIT'),
        coalesce(sum(CASE WHEN side = 'DEBIT' THEN amount ELSE -amount END), 0),
        bool_and(currency = p.currency AND amount = p.amount)
 INTO entry_count, debit_count, signed_total, correct_values
 FROM ledger_entry WHERE posting_id = target;
 IF entry_count <> 2 OR debit_count <> 1 OR signed_total <> 0 OR NOT coalesce(correct_values, false) THEN
  RAISE EXCEPTION 'Posting requires two equal, opposite journal entries in its currency' USING ERRCODE = '23514';
 END IF;
 IF EXISTS (SELECT 1 FROM bank_account WHERE (id = p.source_id OR id = p.destination_id) AND currency <> p.currency) THEN
  RAISE EXCEPTION 'Posting currency differs from account currency' USING ERRCODE = '23514';
 END IF;
 RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER balanced_posting AFTER INSERT ON posting
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION assert_balanced_posting();
CREATE CONSTRAINT TRIGGER balanced_journal AFTER INSERT ON ledger_entry
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION assert_balanced_posting();

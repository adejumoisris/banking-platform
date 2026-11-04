CREATE TABLE fineract_command (
 id UUID PRIMARY KEY,
 target_scope VARCHAR(64) NOT NULL,
 resource_kind VARCHAR(20) NOT NULL CHECK (resource_kind IN ('CUSTOMER','ACCOUNT')),
 local_id UUID NOT NULL,
 external_id VARCHAR(80) NOT NULL,
 payload TEXT NOT NULL,
 status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','PROCESSING','RETRY','SUCCEEDED','REVIEW','FAILED')),
 attempts INTEGER NOT NULL CHECK (attempts >= 0),
 create_attempted BOOLEAN NOT NULL,
 remote_id BIGINT CHECK (remote_id > 0),
 lease_token UUID,
 lease_until TIMESTAMP WITH TIME ZONE,
 available_at TIMESTAMP WITH TIME ZONE NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
 error_code VARCHAR(80),
 CONSTRAINT uq_fineract_local UNIQUE(target_scope, resource_kind, local_id),
 CONSTRAINT uq_fineract_external UNIQUE(target_scope, resource_kind, external_id),
 CONSTRAINT uq_fineract_remote UNIQUE(target_scope, resource_kind, remote_id),
 CONSTRAINT ck_fineract_success CHECK (status <> 'SUCCEEDED' OR remote_id IS NOT NULL),
 CONSTRAINT ck_fineract_lease CHECK (status <> 'PROCESSING' OR (lease_token IS NOT NULL AND lease_until IS NOT NULL))
);
CREATE INDEX idx_fineract_ready ON fineract_command(target_scope, status, available_at);

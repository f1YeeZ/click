ALTER TABLE reviews ADD COLUMN IF NOT EXISTS moderation_reason VARCHAR(500);
ALTER TABLE reviews ADD COLUMN IF NOT EXISTS moderated_by VARCHAR(180);
ALTER TABLE reviews ADD COLUMN IF NOT EXISTS moderated_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY,
    actor_email VARCHAR(180) NOT NULL,
    action VARCHAR(60) NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id VARCHAR(180),
    summary VARCHAR(500) NOT NULL,
    before_state TEXT,
    after_state TEXT,
    reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_created ON audit_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity ON audit_logs(entity_type, entity_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor ON audit_logs(actor_email, created_at DESC);

CREATE TABLE IF NOT EXISTS mouse_import_jobs (
    checksum VARCHAR(64) PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    actor_email VARCHAR(180) NOT NULL,
    created_count INTEGER NOT NULL,
    updated_count INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

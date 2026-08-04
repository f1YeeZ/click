ALTER TABLE mice ADD COLUMN IF NOT EXISTS verification_workflow_status VARCHAR(20) NOT NULL DEFAULT 'OPEN';
ALTER TABLE mice ADD COLUMN IF NOT EXISTS verification_assignee VARCHAR(180);
ALTER TABLE mice ADD COLUMN IF NOT EXISTS verification_note VARCHAR(500);
ALTER TABLE mice ADD COLUMN IF NOT EXISTS verification_due_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE auth_sessions ADD COLUMN IF NOT EXISTS ip_address VARCHAR(64);
ALTER TABLE auth_sessions ADD COLUMN IF NOT EXISTS user_agent VARCHAR(500);

ALTER TABLE mouse_import_jobs ADD COLUMN IF NOT EXISTS total_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE mouse_import_jobs ADD COLUMN IF NOT EXISTS status VARCHAR(30) NOT NULL DEFAULT 'COMPLETED';
ALTER TABLE mouse_import_jobs ADD COLUMN IF NOT EXISTS error_report TEXT;
ALTER TABLE mouse_import_jobs ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP WITH TIME ZONE;
UPDATE mouse_import_jobs SET completed_at = created_at WHERE completed_at IS NULL AND status = 'COMPLETED';

CREATE TABLE IF NOT EXISTS brand_profiles (
    id UUID PRIMARY KEY,
    name VARCHAR(80) NOT NULL UNIQUE,
    official_url VARCHAR(500),
    logo_url VARCHAR(500),
    aliases VARCHAR(500),
    notes VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS content_reports (
    id UUID PRIMARY KEY,
    reporter_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    reporter_email VARCHAR(180) NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id UUID NOT NULL,
    category VARCHAR(40) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    assignee_email VARCHAR(180),
    resolution VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_content_reports_status_created ON content_reports(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_content_reports_target ON content_reports(target_type, target_id, created_at DESC);

CREATE TABLE IF NOT EXISTS admin_notifications (
    id UUID PRIMARY KEY,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(180) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    target_type VARCHAR(40),
    target_id VARCHAR(180),
    read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_admin_notifications_unread ON admin_notifications(read_at, created_at DESC);

CREATE TABLE IF NOT EXISTS system_settings (
    setting_key VARCHAR(100) PRIMARY KEY,
    setting_value VARCHAR(2000) NOT NULL,
    description VARCHAR(500),
    updated_by VARCHAR(180) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

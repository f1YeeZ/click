CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    email VARCHAR(180) NOT NULL UNIQUE,
    password_hash VARCHAR(120) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    hand_size VARCHAR(20),
    hand_length_cm NUMERIC(4,1),
    preferred_grip_style VARCHAR(20),
    terms_accepted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS email_verification_codes (
    id UUID PRIMARY KEY,
    email VARCHAR(180) NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    code_hash VARCHAR(120) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_verification_email_purpose
    ON email_verification_codes(email, purpose, created_at DESC);

CREATE TABLE IF NOT EXISTS mice (
    id UUID PRIMARY KEY,
    brand VARCHAR(80) NOT NULL,
    model VARCHAR(120) NOT NULL,
    variant VARCHAR(100) NOT NULL DEFAULT '',
    slug VARCHAR(180) NOT NULL UNIQUE,
    aliases VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    size_category VARCHAR(20),
    length_mm NUMERIC(7,2),
    width_mm NUMERIC(7,2),
    height_mm NUMERIC(7,2),
    weight_g NUMERIC(7,2),
    shape_type VARCHAR(20),
    hand_compatibility VARCHAR(20),
    sensor_name VARCHAR(120),
    max_dpi INTEGER,
    max_polling_rate_hz INTEGER,
    tracking_speed_ips INTEGER,
    acceleration_g NUMERIC(6,2),
    button_count INTEGER,
    side_button_count INTEGER,
    switch_name VARCHAR(120),
    encoder_name VARCHAR(120),
    connection_modes VARCHAR(120) NOT NULL DEFAULT 'wired',
    material VARCHAR(80),
    material_general VARCHAR(80),
    material_specific VARCHAR(120),
    hump_placement VARCHAR(30),
    front_flare VARCHAR(30),
    side_curvature VARCHAR(30),
    thumb_rest BOOLEAN,
    ring_finger_rest BOOLEAN,
    sensor_type VARCHAR(20),
    adjustable_sensor_position BOOLEAN,
    sensor_position_x NUMERIC(6,2),
    sensor_position_y NUMERIC(6,2),
    sensor_position_x2 NUMERIC(6,2),
    sensor_position_y2 NUMERIC(6,2),
    hot_swappable_switches BOOLEAN,
    switch_type VARCHAR(20),
    switch_life_span_m INTEGER,
    encoder_type VARCHAR(20),
    encoder_steps INTEGER,
    purchase_channels VARCHAR(500),
    image_url VARCHAR(600),
    primary_source_url VARCHAR(600),
    source_notes VARCHAR(1000),
    verified_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_mouse_identity UNIQUE (brand, model, variant)
);

ALTER TABLE mice ADD COLUMN IF NOT EXISTS material_general VARCHAR(80);
ALTER TABLE mice ADD COLUMN IF NOT EXISTS material_specific VARCHAR(120);
ALTER TABLE mice ADD COLUMN IF NOT EXISTS hump_placement VARCHAR(30);
ALTER TABLE mice ADD COLUMN IF NOT EXISTS front_flare VARCHAR(30);
ALTER TABLE mice ADD COLUMN IF NOT EXISTS side_curvature VARCHAR(30);
ALTER TABLE mice ADD COLUMN IF NOT EXISTS thumb_rest BOOLEAN;
ALTER TABLE mice ADD COLUMN IF NOT EXISTS ring_finger_rest BOOLEAN;
ALTER TABLE mice ADD COLUMN IF NOT EXISTS sensor_type VARCHAR(20);
ALTER TABLE mice ADD COLUMN IF NOT EXISTS adjustable_sensor_position BOOLEAN;
ALTER TABLE mice ADD COLUMN IF NOT EXISTS sensor_position_x NUMERIC(6,2);
ALTER TABLE mice ADD COLUMN IF NOT EXISTS sensor_position_y NUMERIC(6,2);
ALTER TABLE mice ADD COLUMN IF NOT EXISTS sensor_position_x2 NUMERIC(6,2);
ALTER TABLE mice ADD COLUMN IF NOT EXISTS sensor_position_y2 NUMERIC(6,2);
ALTER TABLE mice ADD COLUMN IF NOT EXISTS hot_swappable_switches BOOLEAN;
ALTER TABLE mice ADD COLUMN IF NOT EXISTS switch_type VARCHAR(20);
ALTER TABLE mice ADD COLUMN IF NOT EXISTS switch_life_span_m INTEGER;
ALTER TABLE mice ADD COLUMN IF NOT EXISTS encoder_type VARCHAR(20);
ALTER TABLE mice ADD COLUMN IF NOT EXISTS encoder_steps INTEGER;
ALTER TABLE mice ADD COLUMN IF NOT EXISTS purchase_channels VARCHAR(500);
ALTER TABLE mice ADD COLUMN IF NOT EXISTS image_url VARCHAR(600);
ALTER TABLE users ADD COLUMN IF NOT EXISTS hand_size VARCHAR(20);
ALTER TABLE users ADD COLUMN IF NOT EXISTS hand_length_cm NUMERIC(4,1);
ALTER TABLE users ADD COLUMN IF NOT EXISTS preferred_grip_style VARCHAR(20);
ALTER TABLE users ADD COLUMN IF NOT EXISTS terms_accepted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS status_reason VARCHAR(500);
ALTER TABLE users ADD COLUMN IF NOT EXISTS status_changed_by VARCHAR(320);
ALTER TABLE users ADD COLUMN IF NOT EXISTS status_changed_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_mice_status_created ON mice(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_mice_brand ON mice(brand);

CREATE TABLE IF NOT EXISTS reviews (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    mouse_id UUID NOT NULL REFERENCES mice(id),
    grip_style VARCHAR(20),
    hand_size VARCHAR(20),
    usage_duration VARCHAR(30),
    comfort_score INTEGER CHECK (comfort_score BETWEEN 1 AND 10),
    click_score INTEGER CHECK (click_score BETWEEN 1 AND 10),
    scroll_score INTEGER CHECK (scroll_score BETWEEN 1 AND 10),
    build_score INTEGER CHECK (build_score BETWEEN 1 AND 10),
    value_score INTEGER CHECK (value_score BETWEEN 1 AND 10),
    coating_score INTEGER CHECK (coating_score BETWEEN 1 AND 10),
    overall_score NUMERIC(3,1) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_review_user_mouse UNIQUE (user_id, mouse_id)
);

ALTER TABLE reviews ADD COLUMN IF NOT EXISTS coating_score INTEGER;
ALTER TABLE reviews ALTER COLUMN overall_score TYPE NUMERIC(3,1);
ALTER TABLE reviews ALTER COLUMN hand_size DROP NOT NULL;
ALTER TABLE reviews ALTER COLUMN usage_duration DROP NOT NULL;
ALTER TABLE reviews ALTER COLUMN value_score DROP NOT NULL;
ALTER TABLE reviews ALTER COLUMN grip_style DROP NOT NULL;
ALTER TABLE reviews ALTER COLUMN comfort_score DROP NOT NULL;
ALTER TABLE reviews ALTER COLUMN click_score DROP NOT NULL;
ALTER TABLE reviews ALTER COLUMN scroll_score DROP NOT NULL;
ALTER TABLE reviews ALTER COLUMN build_score DROP NOT NULL;
ALTER TABLE reviews ALTER COLUMN coating_score DROP NOT NULL;
ALTER TABLE reviews DROP CONSTRAINT IF EXISTS reviews_comfort_score_check;
ALTER TABLE reviews DROP CONSTRAINT IF EXISTS reviews_click_score_check;
ALTER TABLE reviews DROP CONSTRAINT IF EXISTS reviews_scroll_score_check;
ALTER TABLE reviews DROP CONSTRAINT IF EXISTS reviews_build_score_check;
ALTER TABLE reviews DROP CONSTRAINT IF EXISTS reviews_value_score_check;
ALTER TABLE reviews DROP CONSTRAINT IF EXISTS reviews_coating_score_check;
ALTER TABLE reviews ADD CONSTRAINT reviews_comfort_score_check CHECK (comfort_score BETWEEN 1 AND 10);
ALTER TABLE reviews ADD CONSTRAINT reviews_click_score_check CHECK (click_score BETWEEN 1 AND 10);
ALTER TABLE reviews ADD CONSTRAINT reviews_scroll_score_check CHECK (scroll_score BETWEEN 1 AND 10);
ALTER TABLE reviews ADD CONSTRAINT reviews_build_score_check CHECK (build_score BETWEEN 1 AND 10);
ALTER TABLE reviews ADD CONSTRAINT reviews_value_score_check CHECK (value_score IS NULL OR value_score BETWEEN 1 AND 10);
ALTER TABLE reviews ADD CONSTRAINT reviews_coating_score_check CHECK (coating_score IS NULL OR coating_score BETWEEN 1 AND 10);

CREATE INDEX IF NOT EXISTS idx_reviews_mouse_active ON reviews(mouse_id, status, deleted_at);

CREATE TABLE IF NOT EXISTS review_grip_scores (
    id UUID PRIMARY KEY,
    review_id UUID NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    grip_style VARCHAR(20) NOT NULL,
    comfort_score INTEGER NOT NULL CHECK (comfort_score BETWEEN 1 AND 10),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_review_grip UNIQUE (review_id, grip_style)
);
CREATE INDEX IF NOT EXISTS idx_review_grip_review ON review_grip_scores(review_id);

CREATE TABLE IF NOT EXISTS review_support_positions (
    id UUID PRIMARY KEY,
    review_id UUID NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    position_code VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_review_support_position UNIQUE (review_id, position_code)
);
CREATE INDEX IF NOT EXISTS idx_review_support_review ON review_support_positions(review_id);

CREATE TABLE IF NOT EXISTS review_pro_tags (
    review_id UUID NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    tag_code VARCHAR(40) NOT NULL,
    PRIMARY KEY (review_id, tag_code)
);

CREATE TABLE IF NOT EXISTS review_con_tags (
    review_id UUID NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    tag_code VARCHAR(40) NOT NULL,
    PRIMARY KEY (review_id, tag_code)
);

ALTER TABLE reviews ADD COLUMN IF NOT EXISTS moderation_reason VARCHAR(500);
ALTER TABLE reviews ADD COLUMN IF NOT EXISTS moderated_by VARCHAR(180);
ALTER TABLE reviews ADD COLUMN IF NOT EXISTS moderated_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE users ADD COLUMN IF NOT EXISTS token_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS auth_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    refresh_token_hash VARCHAR(64) NOT NULL UNIQUE,
    token_version BIGINT NOT NULL,
    admin_verified BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_auth_sessions_user_active ON auth_sessions(user_id, revoked_at, expires_at);

CREATE TABLE IF NOT EXISTS admin_login_challenges (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash VARCHAR(120) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_admin_login_challenges_user ON admin_login_challenges(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS security_rate_limit_buckets (
    bucket_key VARCHAR(64) PRIMARY KEY,
    request_count INTEGER NOT NULL,
    window_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_security_rate_limit_expiry ON security_rate_limit_buckets(expires_at);

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

ALTER TABLE mice ADD COLUMN IF NOT EXISTS verification_workflow_status VARCHAR(20) NOT NULL DEFAULT 'OPEN';
ALTER TABLE mice ADD COLUMN IF NOT EXISTS verification_assignee VARCHAR(180);
ALTER TABLE mice ADD COLUMN IF NOT EXISTS verification_note VARCHAR(500);
ALTER TABLE mice ADD COLUMN IF NOT EXISTS verification_due_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE auth_sessions ADD COLUMN IF NOT EXISTS ip_address VARCHAR(64);
ALTER TABLE auth_sessions ADD COLUMN IF NOT EXISTS user_agent VARCHAR(500);
ALTER TABLE auth_sessions ADD COLUMN IF NOT EXISTS admin_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE mouse_import_jobs ADD COLUMN IF NOT EXISTS total_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE mouse_import_jobs ADD COLUMN IF NOT EXISTS status VARCHAR(30) NOT NULL DEFAULT 'COMPLETED';
ALTER TABLE mouse_import_jobs ADD COLUMN IF NOT EXISTS error_report TEXT;
ALTER TABLE mouse_import_jobs ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE IF NOT EXISTS brand_profiles (
    id UUID PRIMARY KEY, name VARCHAR(80) NOT NULL UNIQUE, official_url VARCHAR(500), logo_url VARCHAR(500),
    aliases VARCHAR(500), notes VARCHAR(1000), status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE IF NOT EXISTS content_reports (
    id UUID PRIMARY KEY, reporter_user_id UUID REFERENCES users(id) ON DELETE SET NULL, reporter_email VARCHAR(180) NOT NULL,
    target_type VARCHAR(20) NOT NULL, target_id UUID NOT NULL, category VARCHAR(40) NOT NULL,
    description VARCHAR(1000) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'OPEN', assignee_email VARCHAR(180),
    resolution VARCHAR(1000), created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_content_reports_status_created ON content_reports(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_content_reports_target ON content_reports(target_type, target_id, created_at DESC);
CREATE TABLE IF NOT EXISTS admin_notifications (
    id UUID PRIMARY KEY, type VARCHAR(40) NOT NULL, title VARCHAR(180) NOT NULL, message VARCHAR(1000) NOT NULL,
    target_type VARCHAR(40), target_id VARCHAR(180), read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_admin_notifications_unread ON admin_notifications(read_at, created_at DESC);
CREATE TABLE IF NOT EXISTS system_settings (
    setting_key VARCHAR(100) PRIMARY KEY, setting_value VARCHAR(2000) NOT NULL, description VARCHAR(500),
    updated_by VARCHAR(180) NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

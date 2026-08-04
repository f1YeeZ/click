ALTER TABLE users ADD COLUMN IF NOT EXISTS token_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS auth_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    refresh_token_hash VARCHAR(64) NOT NULL UNIQUE,
    token_version BIGINT NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_auth_sessions_user_active
    ON auth_sessions(user_id, revoked_at, expires_at);

CREATE TABLE IF NOT EXISTS admin_login_challenges (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash VARCHAR(120) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_admin_login_challenges_user
    ON admin_login_challenges(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS security_rate_limit_buckets (
    bucket_key VARCHAR(64) PRIMARY KEY,
    request_count INTEGER NOT NULL,
    window_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_security_rate_limit_expiry
    ON security_rate_limit_buckets(expires_at);

UPDATE auth_sessions
SET revoked_at = CURRENT_TIMESTAMP
WHERE revoked_at IS NULL
  AND user_id IN (
      SELECT id
      FROM users
      WHERE LOWER(email) = 'admin@example.com'
        AND role = 'ADMIN'
  );

UPDATE users
SET email = '2111914551@qq.com',
    token_version = token_version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE LOWER(email) = 'admin@example.com'
  AND role = 'ADMIN';

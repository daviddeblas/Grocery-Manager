/*
 * Manages JWT refresh tokens for maintaining user sessions.
 * When access tokens expire, refresh tokens are used to issue new ones
 * without requiring the user to log in again.
 *
 * Security features:
 * - One token per user (one-to-one relationship)
 * - Explicit expiry date for security
 */
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,   -- The refresh token string
    user_id BIGINT NOT NULL,              -- Associated user
    expiry_date TIMESTAMP NOT NULL,       -- When this token becomes invalid
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens(user_id);
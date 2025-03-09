/*
 * Stores user account information for authentication and authorization.
 * Each user can have multiple shopping lists and store locations.
 *
 * Security features:
 * - Passwords are stored in hashed format (SecurityConfig.java)
 * - Email and username have uniqueness constraints
 */
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE, -- Username for login
    password VARCHAR(120) NOT NULL,       -- Bcrypt-hashed password
    email VARCHAR(100) NOT NULL UNIQUE,   -- Email for notifications and recovery
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    enabled BOOLEAN DEFAULT TRUE          -- Account status flag
);

-- Create indexes to speed up login and email searches
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
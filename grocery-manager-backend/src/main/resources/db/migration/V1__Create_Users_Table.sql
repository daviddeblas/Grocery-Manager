CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       username VARCHAR(50) NOT NULL UNIQUE,
                       password VARCHAR(120) NOT NULL,
                       email VARCHAR(100) NOT NULL UNIQUE,
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       enabled BOOLEAN DEFAULT TRUE
);

-- Create an index on username and email to speed up searches
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
-- R2.1: Initial sessions table
CREATE TABLE IF NOT EXISTS sessions (
    id VARCHAR(128) PRIMARY KEY,
    name VARCHAR(256),
    owner VARCHAR(256),
    created_at BIGINT NOT NULL,
    expires_at BIGINT,
    status VARCHAR(32) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sessions_status ON sessions(status);


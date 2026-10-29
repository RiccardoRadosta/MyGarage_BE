-- ==============================================================================
-- Migrazione V3: Supporto Google Sign-In e rimozione vincolo NOT NULL su password
-- ==============================================================================

ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE users ADD COLUMN IF NOT EXISTS google_sub VARCHAR(255);

ALTER TABLE users ADD CONSTRAINT uq_users_google_sub UNIQUE (google_sub);

CREATE INDEX IF NOT EXISTS idx_users_google_sub ON users (google_sub);

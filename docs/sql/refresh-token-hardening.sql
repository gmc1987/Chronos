-- Refresh tokens created by the hardened IAM service are stored as a
-- 64-character SHA-256 digest. Existing installations use TEXT during the
-- transition so legacy raw-token rows remain readable without truncation.
BEGIN;
ALTER TABLE t_refresh_token ALTER COLUMN token TYPE text;
COMMIT;

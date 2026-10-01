-- Persist OIDC state so PKCE callbacks survive restarts and work across replicas.
CREATE TABLE IF NOT EXISTS iam_oidc_authorization_state (
  state varchar(256) PRIMARY KEY,
  source_code varchar(64) NOT NULL,
  redirect_uri varchar(1000) NOT NULL,
  code_challenge varchar(128) NOT NULL,
  nonce varchar(256) NOT NULL,
  expires_at timestamp NOT NULL,
  consumed_at timestamp
);

CREATE INDEX IF NOT EXISTS idx_iam_oidc_authorization_state_expiry
  ON iam_oidc_authorization_state(expires_at);

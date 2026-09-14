CREATE TABLE IF NOT EXISTS t_consumer_user_role (
    user_id varchar(64) NOT NULL,
    role_id varchar(64) NOT NULL,
    CONSTRAINT pk_consumer_user_role PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_consumer_user_role_user FOREIGN KEY (user_id) REFERENCES t_consumer_user(id),
    CONSTRAINT fk_consumer_user_role_role FOREIGN KEY (role_id) REFERENCES t_role(id)
);

CREATE INDEX IF NOT EXISTS idx_consumer_user_role_role
    ON t_consumer_user_role (role_id);

-- Shared platform-form runtime history for the hospital deployment.
CREATE TABLE IF NOT EXISTS form_instance_revision (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    form_instance_id varchar(64) NOT NULL,
    workflow_instance_id varchar(64) NOT NULL,
    form_id varchar(64) NOT NULL,
    node_key varchar(100) NOT NULL,
    revision_no integer NOT NULL,
    owner varchar(128) NOT NULL,
    status varchar(30) NOT NULL,
    data_json text NOT NULL,
    CONSTRAINT uk_form_revision_sequence UNIQUE (form_instance_id, revision_no)
);

CREATE INDEX IF NOT EXISTS idx_form_revision_workflow
    ON form_instance_revision (workflow_instance_id);

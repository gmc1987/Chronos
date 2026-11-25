CREATE TABLE int_inbound_record (
    run_id varchar(64) NOT NULL REFERENCES int_sync_run(id),
    item_no integer NOT NULL,
    source_key text NOT NULL,
    mapped_json jsonb NOT NULL,
    PRIMARY KEY (run_id,item_no)
);

ALTER TABLE edu_scheduling_agent_proposal
    DROP CONSTRAINT IF EXISTS ck_agent_proposal_status;

ALTER TABLE edu_scheduling_agent_proposal
    ADD CONSTRAINT ck_agent_proposal_status
    CHECK (status IN ('DRAFT', 'CONFIRMED', 'APPLIED', 'REJECTED'));

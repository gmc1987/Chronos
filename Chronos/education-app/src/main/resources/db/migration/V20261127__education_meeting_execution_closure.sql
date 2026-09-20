ALTER TABLE edu_meeting_participant
  ADD COLUMN IF NOT EXISTS checked_in_at timestamp,
  ADD COLUMN IF NOT EXISTS check_in_method varchar(24);

CREATE TABLE IF NOT EXISTS edu_meeting_material (
  id varchar(64) PRIMARY KEY,
  create_by varchar(128) NOT NULL,
  create_time timestamp NOT NULL,
  last_update_by varchar(128),
  last_update_time timestamp,
  meeting_id varchar(64) NOT NULL,
  title varchar(200) NOT NULL,
  file_id varchar(64) NOT NULL,
  CONSTRAINT fk_edu_meeting_material_meeting FOREIGN KEY (meeting_id)
    REFERENCES edu_meeting(id) ON DELETE CASCADE,
  CONSTRAINT uk_edu_meeting_material_file UNIQUE (file_id)
);

CREATE INDEX IF NOT EXISTS idx_edu_meeting_material_meeting
  ON edu_meeting_material(meeting_id, create_time);

CREATE TABLE IF NOT EXISTS edu_meeting_minutes (
  id varchar(64) PRIMARY KEY,
  create_by varchar(128) NOT NULL,
  create_time timestamp NOT NULL,
  last_update_by varchar(128),
  last_update_time timestamp,
  meeting_id varchar(64) NOT NULL,
  content text NOT NULL,
  decisions_text text,
  status varchar(24) NOT NULL DEFAULT 'DRAFT',
  published_at timestamp,
  record_version bigint NOT NULL DEFAULT 0,
  CONSTRAINT fk_edu_meeting_minutes_meeting FOREIGN KEY (meeting_id)
    REFERENCES edu_meeting(id) ON DELETE CASCADE,
  CONSTRAINT uk_edu_meeting_minutes_meeting UNIQUE (meeting_id),
  CONSTRAINT ck_edu_meeting_minutes_status CHECK (status IN ('DRAFT', 'PUBLISHED'))
);

CREATE TABLE IF NOT EXISTS edu_meeting_action_item (
  id varchar(64) PRIMARY KEY,
  create_by varchar(128) NOT NULL,
  create_time timestamp NOT NULL,
  last_update_by varchar(128),
  last_update_time timestamp,
  meeting_id varchar(64) NOT NULL,
  title varchar(200) NOT NULL,
  description text,
  assignee_username varchar(128) NOT NULL,
  due_at timestamp,
  status varchar(24) NOT NULL DEFAULT 'OPEN',
  completed_at timestamp,
  record_version bigint NOT NULL DEFAULT 0,
  CONSTRAINT fk_edu_meeting_action_meeting FOREIGN KEY (meeting_id)
    REFERENCES edu_meeting(id) ON DELETE CASCADE,
  CONSTRAINT ck_edu_meeting_action_status CHECK (
    status IN ('OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED')
  )
);

CREATE INDEX IF NOT EXISTS idx_edu_meeting_action_meeting
  ON edu_meeting_action_item(meeting_id, status, due_at);
CREATE INDEX IF NOT EXISTS idx_edu_meeting_action_assignee
  ON edu_meeting_action_item(assignee_username, status, due_at);

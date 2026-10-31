-- 高职顶岗实习生产主链：单位准入、岗位、三方资料、过程记录、考核和安全事件。
CREATE TABLE IF NOT EXISTS edu_internship_company (
  id varchar(64) PRIMARY KEY, school_id varchar(64) NOT NULL, company_code varchar(64) NOT NULL,
  company_name varchar(200) NOT NULL, credit_code varchar(64), contact_name varchar(100),
  contact_phone varchar(32), address varchar(500), risk_level varchar(16) NOT NULL DEFAULT 'LOW',
  status varchar(24) NOT NULL DEFAULT 'DRAFT', submitted_by varchar(128), submitted_at timestamp,
  approved_by varchar(128), approved_at timestamp, decision_comment varchar(1000), row_version bigint NOT NULL DEFAULT 0,
  create_by varchar(128) NOT NULL, create_time timestamp NOT NULL, last_update_by varchar(128), last_update_time timestamp,
  CONSTRAINT uk_edu_internship_company UNIQUE(school_id, company_code),
  CONSTRAINT ck_edu_internship_company_status CHECK(status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','SUSPENDED')),
  CONSTRAINT ck_edu_internship_company_risk CHECK(risk_level IN ('LOW','MEDIUM','HIGH'))
);
CREATE INDEX IF NOT EXISTS idx_edu_internship_company_scope ON edu_internship_company(school_id,status);

CREATE TABLE IF NOT EXISTS edu_internship_position (
  id varchar(64) PRIMARY KEY, school_id varchar(64) NOT NULL, company_id varchar(64) NOT NULL REFERENCES edu_internship_company(id),
  position_name varchar(200) NOT NULL, capacity integer NOT NULL, mentor_name varchar(100), mentor_phone varchar(32),
  safety_requirements varchar(2000), status varchar(24) NOT NULL DEFAULT 'ACTIVE', row_version bigint NOT NULL DEFAULT 0,
  create_by varchar(128) NOT NULL, create_time timestamp NOT NULL, last_update_by varchar(128), last_update_time timestamp,
  CONSTRAINT ck_edu_internship_position_status CHECK(status IN ('ACTIVE','INACTIVE')),
  CONSTRAINT ck_edu_internship_position_capacity CHECK(capacity > 0)
);
CREATE INDEX IF NOT EXISTS idx_edu_internship_position_company ON edu_internship_position(company_id,status);

CREATE TABLE IF NOT EXISTS edu_internship_placement (
  id varchar(64) PRIMARY KEY, school_id varchar(64) NOT NULL, term_code varchar(32) NOT NULL,
  student_id varchar(64) NOT NULL REFERENCES edu_student_profile(id), company_id varchar(64) NOT NULL REFERENCES edu_internship_company(id),
  position_id varchar(64) NOT NULL REFERENCES edu_internship_position(id), start_date date NOT NULL, end_date date NOT NULL,
  agreement_file_id varchar(64) NOT NULL, consent_file_id varchar(64) NOT NULL, insurance_file_id varchar(64) NOT NULL,
  status varchar(24) NOT NULL DEFAULT 'DRAFT', submitted_by varchar(128), submitted_at timestamp,
  approved_by varchar(128), approved_at timestamp, decision_comment varchar(1000), snapshot_json text,
  snapshot_hash varchar(64), row_version bigint NOT NULL DEFAULT 0,
  create_by varchar(128) NOT NULL, create_time timestamp NOT NULL, last_update_by varchar(128), last_update_time timestamp,
  CONSTRAINT uk_edu_internship_placement UNIQUE(term_code,student_id),
  CONSTRAINT ck_edu_internship_placement_status CHECK(status IN ('DRAFT','SUBMITTED','APPROVED','ACTIVE','COMPLETED','REJECTED','TERMINATED')),
  CONSTRAINT ck_edu_internship_placement_dates CHECK(end_date >= start_date)
);
CREATE INDEX IF NOT EXISTS idx_edu_internship_placement_scope ON edu_internship_placement(school_id,term_code,status);

CREATE TABLE IF NOT EXISTS edu_internship_activity (
  id varchar(64) PRIMARY KEY, placement_id varchar(64) NOT NULL REFERENCES edu_internship_placement(id),
  activity_type varchar(24) NOT NULL, activity_date date NOT NULL, content varchar(4000) NOT NULL,
  result varchar(1000), risk_level varchar(16), status varchar(24) NOT NULL DEFAULT 'RECORDED',
  create_by varchar(128) NOT NULL, create_time timestamp NOT NULL, last_update_by varchar(128), last_update_time timestamp,
  CONSTRAINT ck_edu_internship_activity_type CHECK(activity_type IN ('STUDENT_LOG','SCHOOL_INSPECTION')),
  CONSTRAINT ck_edu_internship_activity_status CHECK(status IN ('RECORDED','CONFIRMED','FOLLOW_UP'))
);
CREATE INDEX IF NOT EXISTS idx_edu_internship_activity_placement ON edu_internship_activity(placement_id,activity_date DESC);

CREATE TABLE IF NOT EXISTS edu_internship_assessment (
  id varchar(64) PRIMARY KEY, placement_id varchar(64) NOT NULL UNIQUE REFERENCES edu_internship_placement(id),
  company_score numeric(5,2) NOT NULL, school_score numeric(5,2) NOT NULL, final_score numeric(5,2) NOT NULL,
  comment varchar(2000), status varchar(24) NOT NULL DEFAULT 'SUBMITTED', submitted_by varchar(128) NOT NULL,
  approved_by varchar(128), approved_at timestamp, row_version bigint NOT NULL DEFAULT 0,
  create_by varchar(128) NOT NULL, create_time timestamp NOT NULL, last_update_by varchar(128), last_update_time timestamp,
  CONSTRAINT ck_edu_internship_assessment_status CHECK(status IN ('SUBMITTED','APPROVED','REJECTED')),
  CONSTRAINT ck_edu_internship_assessment_score CHECK(company_score BETWEEN 0 AND 100 AND school_score BETWEEN 0 AND 100 AND final_score BETWEEN 0 AND 100)
);

CREATE TABLE IF NOT EXISTS edu_internship_incident (
  id varchar(64) PRIMARY KEY, placement_id varchar(64) NOT NULL REFERENCES edu_internship_placement(id),
  occurred_at timestamp NOT NULL, severity varchar(16) NOT NULL, description varchar(4000) NOT NULL,
  emergency_action varchar(2000) NOT NULL, status varchar(24) NOT NULL DEFAULT 'REPORTED',
  reported_by varchar(128) NOT NULL, closed_by varchar(128), closed_at timestamp, closure_comment varchar(2000),
  row_version bigint NOT NULL DEFAULT 0, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
  last_update_by varchar(128), last_update_time timestamp,
  CONSTRAINT ck_edu_internship_incident_severity CHECK(severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
  CONSTRAINT ck_edu_internship_incident_status CHECK(status IN ('REPORTED','INVESTIGATING','CLOSED'))
);
CREATE INDEX IF NOT EXISTS idx_edu_internship_incident_status ON edu_internship_incident(status,severity);

INSERT INTO t_menu(id,create_by,create_time,last_update_by,last_update_time,menu_name,order_num,parent_id,path)
SELECT '76100000-0000-4000-8000-000000000001','SYSTEM',CURRENT_TIMESTAMP,'SYSTEM',CURRENT_TIMESTAMP,
       '实习实训管理',12,(SELECT parent_id FROM t_menu WHERE path='/admin/education/courses' LIMIT 1),
       '/admin/education/internships'
WHERE NOT EXISTS(SELECT 1 FROM t_menu WHERE path='/admin/education/internships');

INSERT INTO t_permission(id,create_by,create_time,last_update_by,last_update_time,permission_code,permission_name,
                         permission_type,action_type,built_in,status,resource_type,scope_type,menu_id)
SELECT source.id,'SYSTEM',CURRENT_TIMESTAMP,'SYSTEM',CURRENT_TIMESTAMP,source.code,source.name,'MENU_ACTION',source.action,
       true,1,'MENU','ROLE',menu.id
FROM (VALUES
 ('76100000-0000-4000-8000-000000000011','education:internship:view','查看实习实训','VIEW'),
 ('76100000-0000-4000-8000-000000000012','education:internship:manage','维护实习实训','MANAGE'),
 ('76100000-0000-4000-8000-000000000013','education:internship:approve','审批实习实训','APPROVE'),
 ('76100000-0000-4000-8000-000000000014','education:internship:safety','处置实习安全事件','MANAGE')
) source(id,code,name,action)
JOIN t_menu menu ON menu.path='/admin/education/internships'
WHERE NOT EXISTS(SELECT 1 FROM t_permission p WHERE p.permission_code=source.code);

INSERT INTO t_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM t_role r CROSS JOIN t_permission p
WHERE r.role_code IN ('SUPER_ADMIN','ROLE_PLATFORM_ADMIN','EDU_ADMIN') AND p.permission_code LIKE 'education:internship:%'
ON CONFLICT DO NOTHING;
INSERT INTO t_role_menu(role_id,menu_id)
SELECT r.id,m.id FROM t_role r CROSS JOIN t_menu m
WHERE r.role_code IN ('SUPER_ADMIN','ROLE_PLATFORM_ADMIN','EDU_ADMIN') AND m.path='/admin/education/internships'
ON CONFLICT DO NOTHING;
INSERT INTO t_role_menu_permission(role_id,menu_id,permission_id)
SELECT r.id,m.id,p.id FROM t_role r CROSS JOIN t_menu m CROSS JOIN t_permission p
WHERE r.role_code IN ('SUPER_ADMIN','ROLE_PLATFORM_ADMIN','EDU_ADMIN') AND m.path='/admin/education/internships'
  AND p.permission_code LIKE 'education:internship:%'
ON CONFLICT DO NOTHING;

-- 教务审核员仅获得查看与审批，不获得维护权限，保证职责分离。
INSERT INTO t_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM t_role r CROSS JOIN t_permission p
WHERE r.role_code='EDU_ACADEMIC_APPROVER' AND p.permission_code IN ('education:internship:view','education:internship:approve','education:internship:safety')
ON CONFLICT DO NOTHING;
INSERT INTO t_role_menu(role_id,menu_id)
SELECT r.id,m.id FROM t_role r CROSS JOIN t_menu m
WHERE r.role_code='EDU_ACADEMIC_APPROVER' AND m.path='/admin/education/internships'
ON CONFLICT DO NOTHING;
INSERT INTO t_role_menu_permission(role_id,menu_id,permission_id)
SELECT r.id,m.id,p.id FROM t_role r CROSS JOIN t_menu m CROSS JOIN t_permission p
WHERE r.role_code='EDU_ACADEMIC_APPROVER' AND m.path='/admin/education/internships'
  AND p.permission_code IN ('education:internship:view','education:internship:approve','education:internship:safety')
ON CONFLICT DO NOTHING;

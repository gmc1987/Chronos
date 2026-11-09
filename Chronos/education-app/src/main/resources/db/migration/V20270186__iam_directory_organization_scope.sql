-- 岗位、职级改为机构级主数据。员工主档保持平台唯一，通过任职关系按机构展示。
ALTER TABLE public.t_position ADD COLUMN IF NOT EXISTS organization_id varchar(64);
ALTER TABLE public.t_job_level ADD COLUMN IF NOT EXISTS organization_id varchar(64);

-- 先移除历史的全局编码唯一约束，否则无法为多机构复制同编码主数据。
ALTER TABLE public.t_position DROP CONSTRAINT IF EXISTS ukjvmie4w7ls34lm32n5p5xy3n;
ALTER TABLE public.t_job_level DROP CONSTRAINT IF EXISTS uklh419pv7gso1w9br59g45nrb3;

-- 旧账号如果已绑定员工但未写机构，以当前主任职回填，供普通管理员数据范围过滤。
UPDATE public.t_admin_user account
SET organization_id = assignment.organization_id,
    last_update_by = 'migration',
    last_update_time = current_timestamp
FROM public.t_employee_assignment assignment
WHERE account.organization_id IS NULL
  AND account.employee_id = assignment.employee_id
  AND assignment.primary_assignment = true
  AND assignment.status = 1
  AND (assignment.effective_from IS NULL OR assignment.effective_from <= current_date)
  AND (assignment.effective_to IS NULL OR assignment.effective_to >= current_date);

-- 一个历史岗位可能被多个机构共用。保留第一个机构使用原记录，其余机构复制后重绑任职。
CREATE TEMP TABLE tmp_position_org_map AS
SELECT p.id AS source_id, o.id AS organization_id,
       row_number() OVER (PARTITION BY p.id ORDER BY o.id) AS rn
FROM public.t_position p
CROSS JOIN public.t_organization o
WHERE p.organization_id IS NULL AND o.status = 1;

UPDATE public.t_position p
SET organization_id = m.organization_id
FROM tmp_position_org_map m
WHERE p.id = m.source_id AND m.rn = 1 AND p.organization_id IS NULL;

INSERT INTO public.t_position
    (id, create_by, create_time, last_update_by, last_update_time, organization_id,
     position_code, position_name, position_category, position_level, management,
     status, sort_order, description)
SELECT md5(m.source_id || '|' || m.organization_id), p.create_by, p.create_time,
       'migration', current_timestamp, m.organization_id, p.position_code,
       p.position_name, p.position_category, p.position_level, p.management,
       p.status, p.sort_order, p.description
FROM tmp_position_org_map m
JOIN public.t_position p ON p.id = m.source_id
WHERE m.rn > 1
  AND NOT EXISTS (SELECT 1 FROM public.t_position x
                  WHERE x.id = md5(m.source_id || '|' || m.organization_id));

UPDATE public.t_employee_assignment a
SET position_id = CASE WHEN m.rn = 1 THEN m.source_id
                       ELSE md5(m.source_id || '|' || m.organization_id) END
FROM tmp_position_org_map m
WHERE a.position_id = m.source_id AND a.organization_id = m.organization_id;

UPDATE public.t_position_role_mapping prm
SET position_id = CASE WHEN m.rn = 1 THEN m.source_id
                       ELSE md5(m.source_id || '|' || m.organization_id) END
FROM tmp_position_org_map m
WHERE prm.position_id = m.source_id AND prm.organization_id = m.organization_id;

CREATE TEMP TABLE tmp_level_org_map AS
SELECT l.id AS source_id, o.id AS organization_id,
       row_number() OVER (PARTITION BY l.id ORDER BY o.id) AS rn
FROM public.t_job_level l
CROSS JOIN public.t_organization o
WHERE l.organization_id IS NULL AND o.status = 1;

UPDATE public.t_job_level l
SET organization_id = m.organization_id
FROM tmp_level_org_map m
WHERE l.id = m.source_id AND m.rn = 1 AND l.organization_id IS NULL;

INSERT INTO public.t_job_level
    (id, create_by, create_time, last_update_by, last_update_time, organization_id,
     level_code, level_name, level_sequence, level_category, status, sort_order, description)
SELECT md5(m.source_id || '|' || m.organization_id), l.create_by, l.create_time,
       'migration', current_timestamp, m.organization_id, l.level_code,
       l.level_name, l.level_sequence, l.level_category, l.status, l.sort_order, l.description
FROM tmp_level_org_map m
JOIN public.t_job_level l ON l.id = m.source_id
WHERE m.rn > 1
  AND NOT EXISTS (SELECT 1 FROM public.t_job_level x
                  WHERE x.id = md5(m.source_id || '|' || m.organization_id));

UPDATE public.t_employee_assignment a
SET job_level_id = CASE WHEN m.rn = 1 THEN m.source_id
                        ELSE md5(m.source_id || '|' || m.organization_id) END
FROM tmp_level_org_map m
WHERE a.job_level_id = m.source_id AND a.organization_id = m.organization_id;

CREATE UNIQUE INDEX IF NOT EXISTS uk_position_org_code
    ON public.t_position(organization_id, position_code) WHERE organization_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_job_level_org_code
    ON public.t_job_level(organization_id, level_code) WHERE organization_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_position_org ON public.t_position(organization_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_job_level_org ON public.t_job_level(organization_id, sort_order);

ALTER TABLE public.t_position ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE public.t_job_level ALTER COLUMN organization_id SET NOT NULL;

DO $$ BEGIN
    ALTER TABLE public.t_position ADD CONSTRAINT fk_position_organization
        FOREIGN KEY (organization_id) REFERENCES public.t_organization(id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
    ALTER TABLE public.t_job_level ADD CONSTRAINT fk_job_level_organization
        FOREIGN KEY (organization_id) REFERENCES public.t_organization(id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

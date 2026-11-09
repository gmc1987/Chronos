-- Cross-module tenant boundary.  Every mutable business aggregate must have an
-- owning organization; UI filtering is not a security boundary.
DO $$
DECLARE
    default_org varchar(64);
BEGIN
    SELECT campus_id INTO default_org
      FROM edu_administrative_class
     WHERE campus_id IS NOT NULL AND campus_id <> ''
     GROUP BY campus_id
     ORDER BY count(*) DESC, campus_id
     LIMIT 1;

    IF default_org IS NULL THEN
        SELECT id INTO default_org
          FROM t_organization
         WHERE status = 1
         ORDER BY CASE WHEN organization_type = 'SCHOOL' THEN 0 ELSE 1 END, create_time
         LIMIT 1;
    END IF;

    IF default_org IS NULL THEN
        RAISE EXCEPTION 'Cannot establish organization ownership for existing business data';
    END IF;

    ALTER TABLE edu_academic_term ADD COLUMN IF NOT EXISTS organization_id varchar(64);
    UPDATE edu_academic_term SET organization_id = default_org
     WHERE organization_id IS NULL OR organization_id = '';
    ALTER TABLE edu_academic_term ALTER COLUMN organization_id SET NOT NULL;

    ALTER TABLE edu_admission_candidate ADD COLUMN IF NOT EXISTS organization_id varchar(64);
    UPDATE edu_admission_candidate candidate
       SET organization_id = class.campus_id
      FROM edu_administrative_class class
     WHERE candidate.planned_class_id = class.id
       AND (candidate.organization_id IS NULL OR candidate.organization_id = '');
    UPDATE edu_admission_candidate SET organization_id = default_org
     WHERE organization_id IS NULL OR organization_id = '';
    ALTER TABLE edu_admission_candidate ALTER COLUMN organization_id SET NOT NULL;

    ALTER TABLE edu_meeting ADD COLUMN IF NOT EXISTS organization_id varchar(64);
    UPDATE edu_meeting meeting
       SET organization_id = room.campus_id
      FROM edu_meeting_room room
     WHERE meeting.room_id = room.id
       AND (meeting.organization_id IS NULL OR meeting.organization_id = '');
    UPDATE edu_meeting SET organization_id = default_org
     WHERE organization_id IS NULL OR organization_id = '';
    ALTER TABLE edu_meeting ALTER COLUMN organization_id SET NOT NULL;

    ALTER TABLE edu_dorm_building ADD COLUMN IF NOT EXISTS organization_id varchar(64);
    UPDATE edu_dorm_building SET organization_id = default_org
     WHERE organization_id IS NULL OR organization_id = '';
    ALTER TABLE edu_dorm_building ALTER COLUMN organization_id SET NOT NULL;

    ALTER TABLE fm_material ADD COLUMN IF NOT EXISTS organization_id varchar(64);
    UPDATE fm_material SET organization_id = default_org
     WHERE organization_id IS NULL OR organization_id = '';
    ALTER TABLE fm_material ALTER COLUMN organization_id SET NOT NULL;

    UPDATE fm_repair_order SET organization_id = default_org
     WHERE organization_id IS NULL OR organization_id = '';
    ALTER TABLE fm_repair_order ALTER COLUMN organization_id SET NOT NULL;

    UPDATE fm_asset SET organization_id = default_org
     WHERE organization_id IS NULL OR organization_id = '';
    ALTER TABLE fm_asset ALTER COLUMN organization_id SET NOT NULL;

    UPDATE kb_knowledge_base SET organization_id = default_org
     WHERE organization_id IS NULL OR organization_id = '';
    ALTER TABLE kb_knowledge_base ALTER COLUMN organization_id SET NOT NULL;
END $$;

CREATE INDEX IF NOT EXISTS idx_edu_term_org ON edu_academic_term(organization_id, start_date DESC);
CREATE INDEX IF NOT EXISTS idx_edu_admission_org ON edu_admission_candidate(organization_id, admission_year DESC, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_edu_meeting_org ON edu_meeting(organization_id, start_time DESC);
CREATE INDEX IF NOT EXISTS idx_edu_dorm_building_org ON edu_dorm_building(organization_id, building_code);
CREATE INDEX IF NOT EXISTS idx_fm_repair_org ON fm_repair_order(organization_id, status, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_fm_material_org ON fm_material(organization_id, material_code);
CREATE INDEX IF NOT EXISTS idx_fm_asset_org ON fm_asset(organization_id, asset_code);
CREATE INDEX IF NOT EXISTS idx_kb_base_org ON kb_knowledge_base(organization_id, create_time DESC);

-- These events carried the verified school identifier before organization_id existed.
with known as (
    select id, substring(detail from '^(?:schoolId|organizationId)=([^,]+)(?:,|$)') as school_id
    from t_audit_log
    where organization_id is null
      and action in ('EDU_TEACHING_CURRENT_TERM_SET', 'EXAM_SETUP_RESOURCE_CREATED',
                     'EDU_STUDENT_CLASS_CREATE', 'EDU_STUDENT_SECTIONS_IMPORT',
                     'EDU_STUDENT_COURSE_EVIDENCE_IMPORT', 'EDUCATION_ASSESSMENT_TEACHER_REGISTERED',
                     'EDUCATION_ASSESSMENT_ACCOUNT_LINKED', 'EDU_ANALYTICS_DAILY_IMPORT')
)
update t_audit_log as audit
set organization_id = organization.id
from known join t_organization as organization on organization.id = known.school_id
where audit.id = known.id and audit.organization_id is null;

-- A program's school cannot be changed. Exclude deleted or unmatched source records.
update t_audit_log as audit
set organization_id = program.school_id
from edu_academic_program as program
join t_organization as organization on organization.id = program.school_id
where audit.organization_id is null
  and audit.action in ('EDU_ACADEMIC_PROGRAM_CREATE', 'EDU_ACADEMIC_PROGRAM_UPDATE',
                       'EDU_ACADEMIC_PROGRAM_COURSE_ADD', 'EDU_ACADEMIC_PROGRAM_SUBMIT',
                       'EDU_ACADEMIC_PROGRAM_PUBLISH')
  and substring(audit.detail from '^programId=([^,]+)(?:,|$)') = program.id;

-- 旧页面曾将员工 ID 写入邀请的 teacher_id；统一为教师档案 ID。
UPDATE edu_preparation_member member
SET teacher_id = teacher.id
FROM edu_teacher_profile teacher
WHERE member.teacher_id = teacher.employee_id
  AND member.teacher_id <> teacher.id;

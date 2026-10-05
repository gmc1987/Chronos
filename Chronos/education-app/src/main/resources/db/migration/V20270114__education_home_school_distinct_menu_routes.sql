-- Give each home-school child menu its own implemented management page.
-- The earlier workbench migration assigned every child the same route.
UPDATE public.t_menu AS child
SET path = CASE child.menu_name
    WHEN '家长管理' THEN '/admin/education/parents'
    WHEN '班级群管理' THEN '/admin/education/home-school/class-groups'
    WHEN '学校通知' THEN '/admin/education/home-school/notices'
    WHEN '家长反馈' THEN '/admin/education/home-school/feedback'
    WHEN '家长会管理' THEN '/admin/education/parent-meetings'
    WHEN '家校沟通记录' THEN '/admin/education/home-school/communications'
END,
last_update_by = 'SYSTEM',
last_update_time = CURRENT_TIMESTAMP
WHERE child.parent_id IN (
    SELECT id FROM public.t_menu WHERE menu_name = '家校中心'
)
AND child.menu_name IN ('家长管理', '班级群管理', '学校通知', '家长反馈', '家长会管理', '家校沟通记录')
AND child.path IS DISTINCT FROM CASE child.menu_name
    WHEN '家长管理' THEN '/admin/education/parents'
    WHEN '班级群管理' THEN '/admin/education/home-school/class-groups'
    WHEN '学校通知' THEN '/admin/education/home-school/notices'
    WHEN '家长反馈' THEN '/admin/education/home-school/feedback'
    WHEN '家长会管理' THEN '/admin/education/parent-meetings'
    WHEN '家校沟通记录' THEN '/admin/education/home-school/communications'
END;

-- Keep the school's reply visible to parents; the former reply action only changed status.
ALTER TABLE public.edu_parent_feedback
    ADD COLUMN IF NOT EXISTS staff_reply text,
    ADD COLUMN IF NOT EXISTS replied_at timestamp;

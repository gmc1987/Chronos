-- 统一教师、学生、家长的账号档案绑定。原家校表的冲突行不覆盖既有身份绑定：
-- 同一家长档案只能保留一个账号，同一账号也只能指向一份档案。
ALTER TABLE public.edu_user_profile_binding
    ADD COLUMN IF NOT EXISTS verified_at timestamp,
    ADD COLUMN IF NOT EXISTS invalidated_at timestamp,
    ADD COLUMN IF NOT EXISTS row_version bigint NOT NULL DEFAULT 0;

UPDATE public.edu_user_profile_binding identity_binding
SET verified_at = COALESCE(identity_binding.verified_at, parent_binding.verified_at),
    invalidated_at = COALESCE(identity_binding.invalidated_at, parent_binding.invalidated_at)
FROM public.edu_parent_account_binding parent_binding
WHERE identity_binding.profile_type = 'PARENT'
  AND identity_binding.profile_id = parent_binding.parent_id
  AND identity_binding.username = parent_binding.username;

-- 仅迁入没有身份绑定的家长。已有一对一绑定时保留原账号，丢弃多余别名。
INSERT INTO public.edu_user_profile_binding (
    id, username, profile_type, profile_id, status, verified_at,
    invalidated_at, row_version, create_by, create_time
)
SELECT DISTINCT ON (parent_binding.parent_id)
    md5('merged-parent-binding-' || parent_binding.username)::uuid::text,
    parent_binding.username,
    'PARENT',
    parent_binding.parent_id,
    parent_binding.status,
    parent_binding.verified_at,
    parent_binding.invalidated_at,
    0,
    'parent_binding_merge',
    CURRENT_TIMESTAMP
FROM public.edu_parent_account_binding parent_binding
JOIN public.edu_parent_profile parent
  ON parent.id = parent_binding.parent_id
JOIN public.t_admin_user account
  ON account.username = parent_binding.username
WHERE parent_binding.status = 'ACTIVE'
  AND parent.status = 'ACTIVE'
  AND account.status = 1
  AND account.account_locked = false
  AND NOT EXISTS (
      SELECT 1 FROM public.edu_user_profile_binding existing
      WHERE existing.profile_type = 'PARENT'
        AND existing.profile_id = parent_binding.parent_id
  )
  AND NOT EXISTS (
      SELECT 1 FROM public.edu_user_profile_binding existing
      WHERE existing.username = parent_binding.username
  )
ORDER BY parent_binding.parent_id, parent_binding.verified_at DESC NULLS LAST,
         parent_binding.create_time, parent_binding.username
ON CONFLICT DO NOTHING;

UPDATE public.edu_user_profile_binding
SET verified_at = COALESCE(verified_at, create_time, CURRENT_TIMESTAMP)
WHERE profile_type = 'PARENT' AND status = 'ACTIVE';

-- 现有 (profile_type, profile_id) 唯一索引保证一个人不会关联多个账号；
-- 再限制一个登录账号只绑定一份教育档案。
CREATE UNIQUE INDEX IF NOT EXISTS uk_edu_user_profile_binding_username
    ON public.edu_user_profile_binding (username);

DROP TABLE public.edu_parent_account_binding;

-- 两个展示别名与已存在的 parent.demo / showcase.parent 重复，且从未登录。
-- 仅清理本次展示脚本创建、没有档案和业务引用的账号；其他账号原样保留。
DELETE FROM public.t_user_role user_role
WHERE user_role.user_id IN (
    SELECT account.id FROM public.t_admin_user account
    WHERE account.username IN ('parent.p-20260001', 'parent.p-20260002')
      AND account.create_by = 'showcase_seed'
      AND account.last_login_at IS NULL
      AND NOT EXISTS (SELECT 1 FROM public.edu_user_profile_binding binding
                      WHERE binding.username = account.username)
      AND NOT EXISTS (SELECT 1 FROM public.edu_meeting_participant participant
                      WHERE participant.username = account.username)
      AND NOT EXISTS (SELECT 1 FROM public.edu_class_group_member member
                      WHERE member.username = account.username)
      AND NOT EXISTS (SELECT 1 FROM public.edu_class_notice_recipient recipient
                      WHERE recipient.recipient_username = account.username)
);

DELETE FROM public.t_admin_user account
WHERE account.username IN ('parent.p-20260001', 'parent.p-20260002')
  AND account.create_by = 'showcase_seed'
  AND account.last_login_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM public.edu_user_profile_binding binding
                  WHERE binding.username = account.username)
  AND NOT EXISTS (SELECT 1 FROM public.edu_meeting_participant participant
                  WHERE participant.username = account.username)
  AND NOT EXISTS (SELECT 1 FROM public.edu_class_group_member member
                  WHERE member.username = account.username)
  AND NOT EXISTS (SELECT 1 FROM public.edu_class_notice_recipient recipient
                  WHERE recipient.recipient_username = account.username);

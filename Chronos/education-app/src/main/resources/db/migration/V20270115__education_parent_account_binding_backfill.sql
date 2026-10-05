-- 家校中心沿用既有的家长身份绑定。只补齐真实、启用的登录账号，
-- 保留家校中心里已有的人工绑定和失效记录，不改变其状态。
INSERT INTO public.edu_parent_account_binding (
    id, parent_id, username, status, verified_at, create_by, create_time
)
SELECT
    md5('education-home-parent-binding-' || identity_binding.username)::uuid::text,
    identity_binding.profile_id,
    identity_binding.username,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    'education_binding_backfill',
    CURRENT_TIMESTAMP
FROM public.edu_user_profile_binding identity_binding
JOIN public.edu_parent_profile parent
  ON parent.id = identity_binding.profile_id AND parent.status = 'ACTIVE'
JOIN public.t_admin_user account
  ON account.username = identity_binding.username
 AND account.status = 1
 AND account.account_locked = false
WHERE identity_binding.profile_type = 'PARENT'
  AND identity_binding.status = 'ACTIVE'
ON CONFLICT (username) DO NOTHING;

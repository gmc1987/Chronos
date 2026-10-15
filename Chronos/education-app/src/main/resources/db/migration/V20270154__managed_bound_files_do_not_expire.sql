-- 24 小时仅用于清理未绑定的上传草稿；正式业务附件按业务生命周期保留。
UPDATE t_managed_file
SET expires_at = NULL
WHERE bind_state = 'BOUND' AND expires_at IS NOT NULL;

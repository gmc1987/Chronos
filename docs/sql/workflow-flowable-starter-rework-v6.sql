-- 为已发布流程升级 Flowable BPMN，使 STARTER 退回策略具备真实的发起人修改任务。
-- 已运行实例继续引用旧部署；这里只清空定义上的发布指针，应用启动恢复器会生成同 Key 的新版本。
BEGIN;

SELECT pg_advisory_xact_lock(hashtext('workflow-flowable-starter-rework-v6'));

UPDATE wf_definition definition
SET flowable_deployment_id = NULL,
    flowable_process_key = NULL,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP
WHERE definition.status = 'PUBLISHED'
  AND definition.flowable_deployment_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM act_re_procdef process_definition
      JOIN act_ge_bytearray resource
        ON resource.deployment_id_ = process_definition.deployment_id_
       AND resource.name_ = process_definition.resource_name_
      WHERE process_definition.deployment_id_ = definition.flowable_deployment_id
        AND position(
            convert_to('chronos_rework__', 'UTF8')
            IN resource.bytes_
        ) > 0
  );

COMMIT;

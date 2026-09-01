-- Chronos 流程中心功能验证数据（PostgreSQL）
-- 前置条件：先启动一次 hospital-app，由 Hibernate 创建/更新表结构。
-- 可重复执行：固定 ID + ON CONFLICT 更新，不清理用户已有数据。
-- 所有测试账号初始密码：Chronos@123

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. 权限与角色
-- ---------------------------------------------------------------------------
INSERT INTO t_permission (id,permission_name,permission_code,permission_type,status,description,create_by,create_time)
VALUES
 ('demo-perm-workflow-use','流程发起与审批','workflow:use','API',1,'流程中心演示权限','demo_seed',now()),
 ('demo-perm-workflow-manage','流程中心管理','workflow:manage','API',1,'流程中心演示管理权限','demo_seed',now())
ON CONFLICT (permission_code) DO UPDATE SET permission_name=EXCLUDED.permission_name,status=1,last_update_by='demo_seed',last_update_time=now();

INSERT INTO t_role (id,role_name,role_code,status,built_in,description,create_by,create_time)
VALUES
 ('demo-role-admin','流程测试管理员','WORKFLOW_DEMO_ADMIN',1,false,'管理流程、表单并参与测试','demo_seed',now()),
 ('demo-role-user','OA流程用户','WORKFLOW_USER',1,false,'可发起和处理流程','demo_seed',now()),
 ('demo-role-hr','HR审批人','HR_APPROVER',1,false,'请假流程HR审批','demo_seed',now()),
 ('demo-role-finance','财务审批人','FINANCE_APPROVER',1,false,'差旅与采购财务审批','demo_seed',now()),
 ('demo-role-purchase','采购审批人','PROCUREMENT_APPROVER',1,false,'采购业务审批','demo_seed',now())
ON CONFLICT (role_code) DO UPDATE SET role_name=EXCLUDED.role_name,status=1,description=EXCLUDED.description,last_update_by='demo_seed',last_update_time=now();

INSERT INTO t_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM t_role r CROSS JOIN t_permission p
WHERE r.role_code IN ('WORKFLOW_DEMO_ADMIN','WORKFLOW_USER','HR_APPROVER','FINANCE_APPROVER','PROCUREMENT_APPROVER')
  AND p.permission_code='workflow:use'
ON CONFLICT DO NOTHING;
INSERT INTO t_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM t_role r CROSS JOIN t_permission p
WHERE r.role_code='WORKFLOW_DEMO_ADMIN' AND p.permission_code='workflow:manage'
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- 2. 机构、部门、岗位、职务、职级
-- ---------------------------------------------------------------------------
INSERT INTO t_organization (id,organization_name,org_code,organization_type,short_name,timezone,status,sort_order,description,register_time,create_by,create_time)
VALUES ('demo-org-hospital','Chronos示范医院','DEMO_HOSPITAL','HOSPITAL','示范医院','Asia/Shanghai',1,1,'流程中心测试机构',now(),'demo_seed',now())
ON CONFLICT (org_code) DO UPDATE SET organization_name=EXCLUDED.organization_name,status=1,last_update_by='demo_seed',last_update_time=now();

INSERT INTO t_organization_unit (id,organization_unit_name,organization_unit_code,unit_type,org_id,parent_organization_unit_id,level,tree_path,sort_order,status,description,create_by,create_time)
VALUES
 ('demo-unit-admin','行政办公室','DEMO_ADMIN','DEPARTMENT','demo-org-hospital',NULL,1,'/demo-unit-admin/',10,1,'普通员工及部门负责人测试部门','demo_seed',now()),
 ('demo-unit-hr','人力资源部','DEMO_HR','DEPARTMENT','demo-org-hospital',NULL,1,'/demo-unit-hr/',20,1,'请假审批部门','demo_seed',now()),
 ('demo-unit-finance','财务部','DEMO_FINANCE','DEPARTMENT','demo-org-hospital',NULL,1,'/demo-unit-finance/',30,1,'财务审批部门','demo_seed',now()),
 ('demo-unit-purchase','采购供应部','DEMO_PURCHASE','DEPARTMENT','demo-org-hospital',NULL,1,'/demo-unit-purchase/',40,1,'采购审批部门','demo_seed',now())
ON CONFLICT (id) DO UPDATE SET organization_unit_name=EXCLUDED.organization_unit_name,status=1,last_update_by='demo_seed',last_update_time=now();

INSERT INTO t_position (id,position_code,position_name,position_category,position_level,management,status,sort_order,description,create_by,create_time)
VALUES
 ('demo-pos-staff','DEMO_STAFF','业务专员','BUSINESS','P1',false,1,10,'普通发起人岗位','demo_seed',now()),
 ('demo-pos-manager','DEMO_MANAGER','部门经理','MANAGEMENT','M1',true,1,20,'部门负责人岗位','demo_seed',now()),
 ('demo-pos-hr','DEMO_HR_SPECIALIST','HR专员','FUNCTION','P2',false,1,30,'HR审批岗位','demo_seed',now()),
 ('demo-pos-finance','DEMO_FINANCE_SPECIALIST','财务专员','FUNCTION','P2',false,1,40,'财务审批岗位','demo_seed',now()),
 ('demo-pos-purchase','DEMO_BUYER','采购专员','BUSINESS','P2',false,1,50,'采购审批岗位','demo_seed',now())
ON CONFLICT (position_code) DO UPDATE SET position_name=EXCLUDED.position_name,status=1,last_update_by='demo_seed',last_update_time=now();

INSERT INTO t_job_title (id,title_code,title_name,title_type,title_level,status,sort_order,create_by,create_time)
VALUES
 ('demo-title-staff','DEMO_TITLE_STAFF','专员','DUTY','JUNIOR',1,10,'demo_seed',now()),
 ('demo-title-manager','DEMO_TITLE_MANAGER','部门经理','DUTY','MIDDLE',1,20,'demo_seed',now())
ON CONFLICT (title_code) DO UPDATE SET title_name=EXCLUDED.title_name,status=1,last_update_by='demo_seed',last_update_time=now();

INSERT INTO t_job_level (id,level_code,level_name,level_sequence,level_category,status,sort_order,description,create_by,create_time)
VALUES
 ('demo-level-p1','DEMO_P1','P1 初级',10,'PROFESSIONAL',1,10,'普通员工','demo_seed',now()),
 ('demo-level-p2','DEMO_P2','P2 中级',20,'PROFESSIONAL',1,20,'专业审批人员','demo_seed',now()),
 ('demo-level-m1','DEMO_M1','M1 基层管理',30,'MANAGEMENT',1,30,'部门负责人','demo_seed',now())
ON CONFLICT (level_code) DO UPDATE SET level_name=EXCLUDED.level_name,status=1,last_update_by='demo_seed',last_update_time=now();

-- ---------------------------------------------------------------------------
-- 3. 员工、任职与账号
-- ---------------------------------------------------------------------------
INSERT INTO t_iam_employee (id,employee_code,employee_name,gender,phone,email,employment_status,employee_type,hire_date,create_by,create_time)
VALUES
 ('demo-emp-admin','DEMO000','流程管理员','MALE','13800000000','wf.admin@demo.local','ACTIVE','STAFF','2024-01-01','demo_seed',now()),
 ('demo-emp-zhang','DEMO001','张三','MALE','13800000001','zhangsan@demo.local','ACTIVE','STAFF','2024-01-01','demo_seed',now()),
 ('demo-emp-li','DEMO002','李经理','FEMALE','13800000002','lisi@demo.local','ACTIVE','STAFF','2023-01-01','demo_seed',now()),
 ('demo-emp-hr1','DEMO003','王HR','FEMALE','13800000003','wanghr@demo.local','ACTIVE','STAFF','2023-03-01','demo_seed',now()),
 ('demo-emp-fin1','DEMO004','赵财务','MALE','13800000004','zhaofin@demo.local','ACTIVE','STAFF','2023-03-01','demo_seed',now()),
 ('demo-emp-fin2','DEMO005','孙财务','FEMALE','13800000005','sunfin@demo.local','ACTIVE','STAFF','2023-03-01','demo_seed',now()),
 ('demo-emp-buy','DEMO006','钱采购','MALE','13800000006','qianbuy@demo.local','ACTIVE','STAFF','2023-03-01','demo_seed',now())
ON CONFLICT (employee_code) DO UPDATE SET employee_name=EXCLUDED.employee_name,employment_status='ACTIVE',last_update_by='demo_seed',last_update_time=now();

INSERT INTO t_employee_assignment (id,employee_id,organization_id,organization_unit_id,position_id,job_level_id,job_title_id,primary_assignment,department_leader,effective_from,status,create_by,create_time)
VALUES
 ('demo-asg-admin','demo-emp-admin','demo-org-hospital','demo-unit-admin','demo-pos-manager','demo-level-m1','demo-title-manager',true,false,'2024-01-01',1,'demo_seed',now()),
 ('demo-asg-zhang','demo-emp-zhang','demo-org-hospital','demo-unit-admin','demo-pos-staff','demo-level-p1','demo-title-staff',true,false,'2024-01-01',1,'demo_seed',now()),
 ('demo-asg-li','demo-emp-li','demo-org-hospital','demo-unit-admin','demo-pos-manager','demo-level-m1','demo-title-manager',true,true,'2023-01-01',1,'demo_seed',now()),
 ('demo-asg-hr1','demo-emp-hr1','demo-org-hospital','demo-unit-hr','demo-pos-hr','demo-level-p2','demo-title-staff',true,true,'2023-03-01',1,'demo_seed',now()),
 ('demo-asg-fin1','demo-emp-fin1','demo-org-hospital','demo-unit-finance','demo-pos-finance','demo-level-p2','demo-title-staff',true,true,'2023-03-01',1,'demo_seed',now()),
 ('demo-asg-fin2','demo-emp-fin2','demo-org-hospital','demo-unit-finance','demo-pos-finance','demo-level-p2','demo-title-staff',true,false,'2023-03-01',1,'demo_seed',now()),
 ('demo-asg-buy','demo-emp-buy','demo-org-hospital','demo-unit-purchase','demo-pos-purchase','demo-level-p2','demo-title-staff',true,true,'2023-03-01',1,'demo_seed',now())
ON CONFLICT (id) DO UPDATE SET status=1,primary_assignment=EXCLUDED.primary_assignment,department_leader=EXCLUDED.department_leader,last_update_by='demo_seed',last_update_time=now();

-- BCrypt(Chronos@123). Spring Security accepts the $2y$ BCrypt prefix.
INSERT INTO t_admin_user (id,username,password,email,display_name,phone,organization_id,position_name,employee_id,account_type,account_locked,failed_login_attempts,must_change_password,token_version,status,password_changed_at,create_by,create_time)
VALUES
 ('demo-user-admin','wf.admin','$2y$10$ftRQHnmZM.C.Sc8zeu1B0uH8lwxjXprrSJjDZFQJd7H9QLL9q2yEq','wf.admin@demo.local','流程管理员','13800000000','demo-org-hospital','部门经理','demo-emp-admin','STAFF',false,0,false,0,1,now(),'demo_seed',now()),
 ('demo-user-zhang','zhangsan','$2y$10$ftRQHnmZM.C.Sc8zeu1B0uH8lwxjXprrSJjDZFQJd7H9QLL9q2yEq','zhangsan@demo.local','张三','13800000001','demo-org-hospital','业务专员','demo-emp-zhang','STAFF',false,0,false,0,1,now(),'demo_seed',now()),
 ('demo-user-li','lisi','$2y$10$ftRQHnmZM.C.Sc8zeu1B0uH8lwxjXprrSJjDZFQJd7H9QLL9q2yEq','lisi@demo.local','李经理','13800000002','demo-org-hospital','部门经理','demo-emp-li','STAFF',false,0,false,0,1,now(),'demo_seed',now()),
 ('demo-user-hr1','wanghr','$2y$10$ftRQHnmZM.C.Sc8zeu1B0uH8lwxjXprrSJjDZFQJd7H9QLL9q2yEq','wanghr@demo.local','王HR','13800000003','demo-org-hospital','HR专员','demo-emp-hr1','STAFF',false,0,false,0,1,now(),'demo_seed',now()),
 ('demo-user-fin1','zhaofin','$2y$10$ftRQHnmZM.C.Sc8zeu1B0uH8lwxjXprrSJjDZFQJd7H9QLL9q2yEq','zhaofin@demo.local','赵财务','13800000004','demo-org-hospital','财务专员','demo-emp-fin1','STAFF',false,0,false,0,1,now(),'demo_seed',now()),
 ('demo-user-fin2','sunfin','$2y$10$ftRQHnmZM.C.Sc8zeu1B0uH8lwxjXprrSJjDZFQJd7H9QLL9q2yEq','sunfin@demo.local','孙财务','13800000005','demo-org-hospital','财务专员','demo-emp-fin2','STAFF',false,0,false,0,1,now(),'demo_seed',now()),
 ('demo-user-buy','qianbuy','$2y$10$ftRQHnmZM.C.Sc8zeu1B0uH8lwxjXprrSJjDZFQJd7H9QLL9q2yEq','qianbuy@demo.local','钱采购','13800000006','demo-org-hospital','采购专员','demo-emp-buy','STAFF',false,0,false,0,1,now(),'demo_seed',now())
ON CONFLICT (username) DO UPDATE SET display_name=EXCLUDED.display_name,employee_id=EXCLUDED.employee_id,status=1,account_locked=false,last_update_by='demo_seed',last_update_time=now();

INSERT INTO t_user_role(user_id,role_id)
SELECT u.id,r.id FROM t_admin_user u JOIN t_role r ON
 (u.username='wf.admin' AND r.role_code IN ('WORKFLOW_DEMO_ADMIN','WORKFLOW_USER')) OR
 (u.username IN ('zhangsan','lisi') AND r.role_code='WORKFLOW_USER') OR
 (u.username='wanghr' AND r.role_code IN ('WORKFLOW_USER','HR_APPROVER')) OR
 (u.username IN ('zhaofin','sunfin') AND r.role_code IN ('WORKFLOW_USER','FINANCE_APPROVER')) OR
 (u.username='qianbuy' AND r.role_code IN ('WORKFLOW_USER','PROCUREMENT_APPROVER'))
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- 4. OA 主表单
-- ---------------------------------------------------------------------------
INSERT INTO form_definition (id,form_key,form_name,version,status,description,published_at,create_by,create_time)
VALUES
 ('demo-form-leave','OA_LEAVE','请假申请单','v1','PUBLISHED','验证日期、天数、条件分支和字段权限',now(),'demo_seed',now()),
 ('demo-form-travel','OA_TRAVEL','差旅申请单','v1','PUBLISHED','验证部门负责人和财务角色审批',now(),'demo_seed',now()),
 ('demo-form-purchase','OA_PURCHASE','采购申请单','v1','PUBLISHED','验证金额条件、采购审批和财务会签',now(),'demo_seed',now())
ON CONFLICT (form_key,version) DO UPDATE SET form_name=EXCLUDED.form_name,status='PUBLISHED',description=EXCLUDED.description,last_update_by='demo_seed',last_update_time=now();

INSERT INTO form_field (id,form_id,field_key,field_label,field_type,sort_order,required,options_json,create_by,create_time)
VALUES
 ('demo-fld-leave-type','demo-form-leave','leaveType','请假类型','SELECT',10,true,'[{"label":"年假","value":"ANNUAL"},{"label":"病假","value":"SICK"},{"label":"事假","value":"PERSONAL"}]','demo_seed',now()),
 ('demo-fld-leave-start','demo-form-leave','startDate','开始日期','DATE',20,true,'[]','demo_seed',now()),
 ('demo-fld-leave-end','demo-form-leave','endDate','结束日期','DATE',30,true,'[]','demo_seed',now()),
 ('demo-fld-leave-days','demo-form-leave','days','请假天数','NUMBER',40,true,'[]','demo_seed',now()),
 ('demo-fld-leave-reason','demo-form-leave','reason','请假事由','TEXTAREA',50,true,'[]','demo_seed',now()),
 ('demo-fld-travel-city','demo-form-travel','destination','出差地点','TEXT',10,true,'[]','demo_seed',now()),
 ('demo-fld-travel-start','demo-form-travel','startDate','出发日期','DATE',20,true,'[]','demo_seed',now()),
 ('demo-fld-travel-end','demo-form-travel','endDate','返回日期','DATE',30,true,'[]','demo_seed',now()),
 ('demo-fld-travel-budget','demo-form-travel','budget','预算金额','NUMBER',40,true,'[]','demo_seed',now()),
 ('demo-fld-travel-purpose','demo-form-travel','purpose','出差事由','TEXTAREA',50,true,'[]','demo_seed',now()),
 ('demo-fld-buy-name','demo-form-purchase','itemName','采购物品','TEXT',10,true,'[]','demo_seed',now()),
 ('demo-fld-buy-qty','demo-form-purchase','quantity','数量','NUMBER',20,true,'[]','demo_seed',now()),
 ('demo-fld-buy-amount','demo-form-purchase','amount','采购总额','NUMBER',30,true,'[]','demo_seed',now()),
 ('demo-fld-buy-supplier','demo-form-purchase','supplier','建议供应商','TEXT',40,false,'[]','demo_seed',now()),
 ('demo-fld-buy-reason','demo-form-purchase','reason','采购原因','TEXTAREA',50,true,'[]','demo_seed',now())
ON CONFLICT (form_id,field_key) DO UPDATE SET field_label=EXCLUDED.field_label,field_type=EXCLUDED.field_type,sort_order=EXCLUDED.sort_order,required=EXCLUDED.required,options_json=EXCLUDED.options_json,last_update_by='demo_seed',last_update_time=now();

-- ---------------------------------------------------------------------------
-- 5. 三套已发布 OA 流程定义
-- ---------------------------------------------------------------------------
INSERT INTO wf_definition (id,flow_code,flow_name,category,version,description,entry_node_key,status,tags,config_json,main_form_id,manager_user,starter_scope_json,ai_assist_enabled,published_at,create_by,create_time)
VALUES
 ('demo-flow-leave','OA_LEAVE_APPROVAL','请假审批','HR','v1','3天及以下部门负责人审批，超过3天追加HR审批','start','PUBLISHED','OA,请假,演示','{}','demo-form-leave','wf.admin','{"type":"ALL"}',false,now(),'demo_seed',now()),
 ('demo-flow-travel','OA_TRAVEL_APPROVAL','差旅审批','ADMIN','v1','部门负责人审批后由任意一名财务审批','start','PUBLISHED','OA,差旅,演示','{}','demo-form-travel','wf.admin','{"type":"ALL"}',false,now(),'demo_seed',now()),
 ('demo-flow-purchase','OA_PURCHASE_APPROVAL','采购审批','PURCHASE','v1','低额采购走采购审批，高额采购追加财务全员会签','start','PUBLISHED','OA,采购,演示','{}','demo-form-purchase','wf.admin','{"type":"ALL"}',false,now(),'demo_seed',now())
ON CONFLICT (flow_code,version) DO UPDATE SET flow_name=EXCLUDED.flow_name,status='PUBLISHED',main_form_id=EXCLUDED.main_form_id,entry_node_key='start',description=EXCLUDED.description,last_update_by='demo_seed',last_update_time=now();

-- 节点字段权限约定：发起人可编辑；审批节点主表单只读。
INSERT INTO wf_node (id,flow_id,node_key,node_name,node_type,executor,timeout_sec,retry_max,retry_interval_sec,input_schema,output_schema,properties_json,additional_form_ids,field_permissions_json,create_by,create_time)
VALUES
 ('demo-node-leave-start','demo-flow-leave','start','开始','START','',0,0,0,'','','{"position":{"x":80,"y":180}}','[]','{"permissions":{},"required":{}}','demo_seed',now()),
 ('demo-node-leave-manager','demo-flow-leave','manager','部门负责人审批','APPROVAL','',0,0,0,'','','{"position":{"x":260,"y":180},"assigneeMode":"INITIATOR_MANAGER","assigneeValue":"","approvalMode":"SINGLE","dueHours":24,"returnPolicy":"PREVIOUS"}','[]','{"permissions":{"demo-form-leave.leaveType":"READ","demo-form-leave.startDate":"READ","demo-form-leave.endDate":"READ","demo-form-leave.days":"READ","demo-form-leave.reason":"READ"},"required":{}}','demo_seed',now()),
 ('demo-node-leave-gateway','demo-flow-leave','days_gate','请假天数判断','CONDITION','',0,0,0,'','','{"position":{"x":440,"y":180}}','[]','{"permissions":{},"required":{}}','demo_seed',now()),
 ('demo-node-leave-hr','demo-flow-leave','hr','HR审批','APPROVAL','',0,0,0,'','','{"position":{"x":620,"y":90},"assigneeMode":"ROLE","assigneeValue":"HR_APPROVER","approvalMode":"SINGLE","dueHours":24,"returnPolicy":"PREVIOUS"}','[]','{"permissions":{"demo-form-leave.leaveType":"READ","demo-form-leave.startDate":"READ","demo-form-leave.endDate":"READ","demo-form-leave.days":"READ","demo-form-leave.reason":"READ"},"required":{}}','demo_seed',now()),
 ('demo-node-leave-end','demo-flow-leave','end','结束','END','',0,0,0,'','','{"position":{"x":800,"y":180}}','[]','{"permissions":{},"required":{}}','demo_seed',now()),

 ('demo-node-travel-start','demo-flow-travel','start','开始','START','',0,0,0,'','','{"position":{"x":80,"y":180}}','[]','{"permissions":{},"required":{}}','demo_seed',now()),
 ('demo-node-travel-manager','demo-flow-travel','manager','部门负责人审批','APPROVAL','',0,0,0,'','','{"position":{"x":280,"y":180},"assigneeMode":"INITIATOR_MANAGER","assigneeValue":"","approvalMode":"SINGLE","dueHours":24,"returnPolicy":"PREVIOUS"}','[]','{"permissions":{"demo-form-travel.destination":"READ","demo-form-travel.startDate":"READ","demo-form-travel.endDate":"READ","demo-form-travel.budget":"READ","demo-form-travel.purpose":"READ"},"required":{}}','demo_seed',now()),
 ('demo-node-travel-fin','demo-flow-travel','finance','财务审批（任一人）','APPROVAL','',0,0,0,'','','{"position":{"x":500,"y":180},"assigneeMode":"ROLE","assigneeValue":"FINANCE_APPROVER","approvalMode":"ANY","dueHours":48,"returnPolicy":"PREVIOUS"}','[]','{"permissions":{"demo-form-travel.destination":"READ","demo-form-travel.startDate":"READ","demo-form-travel.endDate":"READ","demo-form-travel.budget":"READ","demo-form-travel.purpose":"READ"},"required":{}}','demo_seed',now()),
 ('demo-node-travel-end','demo-flow-travel','end','结束','END','',0,0,0,'','','{"position":{"x":720,"y":180}}','[]','{"permissions":{},"required":{}}','demo_seed',now()),

 ('demo-node-buy-start','demo-flow-purchase','start','开始','START','',0,0,0,'','','{"position":{"x":80,"y":180}}','[]','{"permissions":{},"required":{}}','demo_seed',now()),
 ('demo-node-buy-manager','demo-flow-purchase','manager','部门负责人审批','APPROVAL','',0,0,0,'','','{"position":{"x":250,"y":180},"assigneeMode":"INITIATOR_MANAGER","assigneeValue":"","approvalMode":"SINGLE","dueHours":24,"returnPolicy":"PREVIOUS"}','[]','{"permissions":{"demo-form-purchase.itemName":"READ","demo-form-purchase.quantity":"READ","demo-form-purchase.amount":"READ","demo-form-purchase.supplier":"READ","demo-form-purchase.reason":"READ"},"required":{}}','demo_seed',now()),
 ('demo-node-buy-gateway','demo-flow-purchase','amount_gate','采购金额判断','CONDITION','',0,0,0,'','','{"position":{"x":420,"y":180}}','[]','{"permissions":{},"required":{}}','demo_seed',now()),
 ('demo-node-buy-proc','demo-flow-purchase','procurement','采购审批','APPROVAL','',0,0,0,'','','{"position":{"x":610,"y":270},"assigneeMode":"ROLE","assigneeValue":"PROCUREMENT_APPROVER","approvalMode":"SINGLE","dueHours":48,"returnPolicy":"PREVIOUS"}','[]','{"permissions":{"demo-form-purchase.itemName":"READ","demo-form-purchase.quantity":"READ","demo-form-purchase.amount":"READ","demo-form-purchase.supplier":"EDIT","demo-form-purchase.reason":"READ"},"required":{}}','demo_seed',now()),
 ('demo-node-buy-fin','demo-flow-purchase','finance_all','财务会签','APPROVAL','',0,0,0,'','','{"position":{"x":610,"y":90},"assigneeMode":"ROLE","assigneeValue":"FINANCE_APPROVER","approvalMode":"ALL","dueHours":48,"returnPolicy":"PREVIOUS"}','[]','{"permissions":{"demo-form-purchase.itemName":"READ","demo-form-purchase.quantity":"READ","demo-form-purchase.amount":"READ","demo-form-purchase.supplier":"READ","demo-form-purchase.reason":"READ"},"required":{}}','demo_seed',now()),
 ('demo-node-buy-end','demo-flow-purchase','end','结束','END','',0,0,0,'','','{"position":{"x":820,"y":180}}','[]','{"permissions":{},"required":{}}','demo_seed',now())
ON CONFLICT (flow_id,node_key) DO UPDATE SET node_name=EXCLUDED.node_name,node_type=EXCLUDED.node_type,properties_json=EXCLUDED.properties_json,additional_form_ids=EXCLUDED.additional_form_ids,field_permissions_json=EXCLUDED.field_permissions_json,last_update_by='demo_seed',last_update_time=now();

-- 重复执行前只删除本演示流程的连线，避免 wf_edge 没有业务唯一约束造成重复。
DELETE FROM wf_edge WHERE flow_id IN ('demo-flow-leave','demo-flow-travel','demo-flow-purchase');
INSERT INTO wf_edge (id,flow_id,from_node_key,to_node_key,condition_expr,is_default,create_by,create_time)
VALUES
 ('demo-edge-leave-1','demo-flow-leave','start','manager','',false,'demo_seed',now()),
 ('demo-edge-leave-2','demo-flow-leave','manager','days_gate','',false,'demo_seed',now()),
 ('demo-edge-leave-3','demo-flow-leave','days_gate','hr','days > 3',false,'demo_seed',now()),
 ('demo-edge-leave-4','demo-flow-leave','days_gate','end','',true,'demo_seed',now()),
 ('demo-edge-leave-5','demo-flow-leave','hr','end','',false,'demo_seed',now()),
 ('demo-edge-travel-1','demo-flow-travel','start','manager','',false,'demo_seed',now()),
 ('demo-edge-travel-2','demo-flow-travel','manager','finance','',false,'demo_seed',now()),
 ('demo-edge-travel-3','demo-flow-travel','finance','end','',false,'demo_seed',now()),
 ('demo-edge-buy-1','demo-flow-purchase','start','manager','',false,'demo_seed',now()),
 ('demo-edge-buy-2','demo-flow-purchase','manager','amount_gate','',false,'demo_seed',now()),
 ('demo-edge-buy-3','demo-flow-purchase','amount_gate','finance_all','amount >= 10000',false,'demo_seed',now()),
 ('demo-edge-buy-4','demo-flow-purchase','amount_gate','procurement','',true,'demo_seed',now()),
 ('demo-edge-buy-5','demo-flow-purchase','finance_all','procurement','',false,'demo_seed',now()),
 ('demo-edge-buy-6','demo-flow-purchase','procurement','end','',false,'demo_seed',now());

COMMIT;

-- 快速核对
SELECT username,display_name,status FROM t_admin_user WHERE username IN ('wf.admin','zhangsan','lisi','wanghr','zhaofin','sunfin','qianbuy') ORDER BY username;
SELECT flow_code,flow_name,version,status FROM wf_definition WHERE flow_code LIKE 'OA_%_APPROVAL' ORDER BY flow_code;

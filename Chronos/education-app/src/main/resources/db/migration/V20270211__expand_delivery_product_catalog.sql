INSERT INTO cd_product_catalog (module_code, display_name, enabled, sort_order)
VALUES
    ('CAMPUS_MEETINGS', '校园会议', true, 40),
    ('EDU_TEACHING_OPERATIONS', '教学运行与排课', false, 50),
    ('CAMPUS_VENUE', '校园场地预约', false, 60),
    ('EDU_STUDENT_LIFECYCLE', '招生与学籍', false, 70),
    ('EDU_TEACHER_PERSONNEL', '教师人事', false, 80),
    ('EDU_ACCOMMODATION', '宿舍管理', false, 90),
    ('EDU_INTERNSHIP', '实习实训', false, 100),
    ('EDU_EXAM', '考务管理', false, 110),
    ('EDU_GRADES', '成绩与补考', false, 120),
    ('EDU_TEACHING_RESOURCES', '教学资源与教研', false, 130),
    ('EDU_HOMEWORK', '作业与学情', false, 140),
    ('EDU_SUPERVISION', '教学督导', false, 150),
    ('EDU_HOME_SCHOOL', '家校与班级协同', false, 160),
    ('CAMPUS_PUBLICATIONS', '通知公告', false, 170),
    ('COLLABORATION_OFFICE', '协同办公与流程配置', false, 180),
    ('SHARED_DOCUMENTS', '共享文档', false, 190),
    ('ASSET_MANAGEMENT', '资产管理', false, 200),
    ('AI_KNOWLEDGE', 'AI 知识库', false, 210),
    ('EDU_OPERATIONS_AGENT', '学期运营智能体', false, 220),
    ('EDU_ANALYTICS', '校务数据分析', false, 230),
    ('THIRD_PARTY_INTEGRATIONS', '第三方集成管理', false, 240)
ON CONFLICT (module_code) DO NOTHING;

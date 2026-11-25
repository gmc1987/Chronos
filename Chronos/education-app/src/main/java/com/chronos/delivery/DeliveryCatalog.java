package com.chronos.delivery;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

final class DeliveryCatalog {
	private DeliveryCatalog() {
	}

	record Product(String code, String name, List<String> dependencies, List<String> endpoints,
			List<String> menus, Set<String> readPermissions, Set<String> writePermissions, String pendingReason) {
		Product(String code, String name, List<String> dependencies, List<String> endpoints, List<String> menus,
				Set<String> readPermissions, Set<String> writePermissions) {
			this(code, name, dependencies, endpoints, menus, readPermissions, writePermissions, "");
		}

		boolean issuable() {
			return pendingReason.isEmpty();
		}

		boolean allows(String method, String path) {
			return endpoints.stream().anyMatch(rule -> {
				int separator = rule.indexOf(':');
				if (separator < 0 || rule.startsWith("/")) {
					return matches(path, rule);
				}
				return method.equals(rule.substring(0, separator)) && path.equals(rule.substring(separator + 1));
			});
		}
	}

	static final List<Product> PRODUCTS = List.of(
			new Product("TERM_CALENDAR", "学期与校历", List.of(),
					List.of("/admin/education/terms", "/admin/education/calendar-days", "/admin/education/holidays",
							"/admin/education/bell-schedules", "/admin/education/bell-periods"),
					List.of("/admin/education/terms", "/admin/education/holidays"),
					Set.of("education:term:view"), Set.of("education:term:create", "education:term:update",
							"education:term:delete", "education:term:manage")),
			new Product("FACILITY_REPAIR", "故障报修", List.of(),
					List.of("/admin/facility/repair-orders", "/admin/facility/technicians", "/admin/facility/organization-options",
							"/admin/facility/materials", "/admin/facility/material-requests", "/admin/facility/ledger",
							"/portal/facility", "/portal/files"),
					List.of("/admin/facility/repairs", "/admin/facility/technicians", "/admin/facility/materials",
							"/admin/facility/material-requests", "/admin/facility/ledger", "/portal/facility/repairs"),
					Set.of("facility:repair:view", "facility:technician:view", "facility:material:view",
							"facility:ledger:view", "facility:material-request:view"),
					Set.of("facility:repair:manage", "facility:technician:manage", "facility:material:manage", "facility:material-request:manage")),
			new Product("FACILITY_GOVERNANCE", "现有资产与保养", List.of("FACILITY_REPAIR"),
					List.of("/admin/facility/governance"), List.of("/admin/facility/assets"),
					Set.of("facility:asset:view"), Set.of("facility:asset:manage", "facility:safety:manage")),
			new Product("CAMPUS_MEETINGS", "校园会议", List.of(),
					List.of("/admin/education/meetings", "/admin/education/meeting/rooms",
							"/admin/education/meeting/participant-options", "/admin/education/calendar/binding",
							"/portal/education/meetings"),
					List.of("/admin/education/meeting/manage", "/admin/education/meeting/rooms",
							"/admin/education/meeting/recurring", "/portal/education/meetings"),
					Set.of("education:meeting:view", "education:meeting:room:view", "education:meeting:series:view",
							"education:meeting:calendar:view"),
					Set.of("education:meeting:manage", "education:meeting:room:manage", "education:meeting:series:manage",
							"education:meeting:calendar:manage")),
			new Product("EDU_TEACHING_OPERATIONS", "教学运行与排课", List.of(),
					entries("""
					/admin/education/academic-lifecycle/programs /admin/education/academic-lifecycle/program-import
					/admin/education/academic-lifecycle/term-options /admin/education/term-course-plans
					POST:/admin/education/academic-lifecycle/student-setup/grade
					POST:/admin/education/academic-lifecycle/student-setup/major
					POST:/admin/education/academic-lifecycle/student-setup/administrative-class
					POST:/admin/education/academic-lifecycle/student-setup/course
					GET:/admin/education/grades GET:/admin/education/majors GET:/admin/education/subjects
					GET:/admin/education/administrative-classes GET:/admin/education/teachers
					GET:/admin/education/courses GET:/admin/education/terms GET:/admin/education/bell-schedules
					/admin/education/schedule-dimension-options /admin/education/schedule-student-options
					/admin/education/schedule-candidates /admin/education/schedule-versions
					/admin/education/schedule-policy /admin/education/schedule-generation-jobs /admin/education/schedules
					/admin/education/course-offerings /admin/education/teaching-class-members
					GET:/admin/education/teaching-assignments /admin/education/teacher-time-constraints
					/admin/education/classroom-unavailable-slots /admin/education/classroom-management
					/admin/education/classrooms /admin/education/teaching-hours /admin/education/schedule-occurrences
					/admin/education/schedule-date-exceptions /admin/education/schedule-pending-makeups
					/admin/education/schedule-pending-substitutions /admin/education/teaching-suspensions
					/admin/education/course-adjustment-incidents /portal/education/schedule GET:/portal/education/student-contexts
					"""),
					entries("""
					/admin/education/academic-lifecycle /admin/education/term-course-plans /admin/education/scheduling
					/admin/education/teaching-hours /admin/education/teaching-assignments
					/admin/education/teaching-class-members /admin/education/classroom-management
					/portal/education/schedule
					"""),
					Set.copyOf(entries("""
					education:academic-program:view education:term-course-plan:view education:scheduling:view
					education:resource:venue:view education:term:view education:grade:view education:class:view
					education:subject:view education:teacher:view education:course:view education:teaching-assignment:view
					""")),
					Set.copyOf(entries("""
					education:academic-program:review education:term-course-plan:manage education:scheduling:manage education:scheduling:review
					education:resource:venue:manage education:academic-program:manage education:grade:create
					education:class:create education:course:create education:term-course-plan:setup
					"""))),
			new Product("CAMPUS_VENUE", "校园场地预约", List.of(),
					List.of("/admin/education/classroom-management", "/admin/education/classroom-reservations/desk",
							"/portal/education/classroom-reservations", "/portal/education/classrooms/options"),
					List.of("/admin/education/classroom-management", "/admin/education/classroom-reservations",
							"/portal/education/classroom-reservations"),
					Set.of("education:resource:venue:view", "education:venue-reservation:apply"),
					Set.of("education:resource:venue:manage")),
			new Product("EDU_STUDENT_LIFECYCLE", "招生与学籍", List.of(),
					entries("""
					/admin/education/admissions GET:/admin/education/students
					GET:/admin/education/administrative-classes GET:/admin/education/majors GET:/admin/education/grades
					/admin/education/academic-lifecycle/programs /admin/education/academic-lifecycle/program-import
					/admin/education/academic-lifecycle/term-options /admin/education/academic-lifecycle/student-options
					/admin/education/academic-lifecycle/student-setup /admin/education/academic-lifecycle/offering-options
					/admin/education/academic-lifecycle/program-options /admin/education/academic-lifecycle/registrations
					/admin/education/academic-lifecycle/enrollments /admin/education/academic-lifecycle/graduation-audits
					"""),
					entries("""
					/admin/education/admissions /admin/education/academic-lifecycle /admin/education/term-registrations
					/admin/education/course-enrollments /admin/education/graduation-audits
					"""),
					Set.copyOf(entries("""
					education:admission:view education:student:view education:class:view education:grade:view
					education:academic-program:view education:term-registration:view
					education:course-enrollment:view education:graduation-audit:view
					""")),
					Set.copyOf(entries("""
					education:admission:import education:admission:review education:admission:enroll education:admission:register
					education:class:create education:grade:create education:course:create
					education:academic-program:manage education:academic-program:review education:term-registration:manage
					education:course-enrollment:manage education:graduation-audit:manage education:graduation-audit:approve
					"""))),
			new Product("EDU_TEACHER_PERSONNEL", "教师人事", List.of(),
					entries("""
					/admin/education/teacher-personnel /portal/education/teacher-personnel/mine
					POST:/portal/education/teacher-personnel/resignations
					"""),
					entries("""
					/admin/education/teacher-personnel /portal/education/teacher-personnel
					"""),
					Set.copyOf(entries("""
					education:teacher:business:view
					""")),
					Set.copyOf(entries("""
					education:teacher:business:manage
					"""))),
			new Product("EDU_ACCOMMODATION", "宿舍管理", List.of(),
					List.of("/admin/education/accommodations", "GET:/admin/education/terms"),
					List.of("/admin/education/accommodations"),
					Set.of("education:accommodation:view", "education:term:view"),
					Set.of("education:accommodation:manage")),
			new Product("EDU_INTERNSHIP", "实习实训", List.of(),
					List.of("/admin/education/internships"),
					List.of("/admin/education/internships"),
					Set.of("education:internship:view"),
					Set.of("education:internship:manage", "education:internship:approve", "education:internship:safety")),
			new Product("EDU_EXAM", "考务管理", List.of(),
					List.of("/admin/education/exam", "/portal/education/exam"),
					List.of("/admin/education/exam/plans", "/admin/education/exam/invigilation",
							"/admin/education/exam/rooms", "/portal/education/exam/invigilation",
							"/portal/education/exam/my-exams"),
					Set.of("education:exam:plan:view", "education:exam:room:view",
							"education:exam:invigilation:view", "education:exam:incident:view", "education:exam:ticket:view"),
					Set.of("education:exam:plan:manage", "education:exam:room:manage",
							"education:exam:invigilation:manage", "education:exam:change:approve",
							"education:exam:incident:manage")),
			new Product("EDU_GRADES", "成绩与补考", List.of(),
					entries("""
					/admin/education/grades/schemes /admin/education/grades/offerings /admin/education/grades/gradebooks
					/admin/education/grades/roster-setup /admin/education/grade-production
					/admin/education/grade-analysis /portal/education/grades
					"""),
					entries("""
					/admin/education/score-center /admin/education/score-center/class-analysis
					/admin/education/score-center/grade-analysis /admin/education/score-center/subject-analysis
					/admin/education/score-center/trend-analysis /portal/education/grades
					"""),
					Set.copyOf(entries("""
					education:score:scheme:view education:score:gradebook:view education:score:class-analysis:view
					education:score:grade-analysis:view education:score:subject-analysis:view education:score:trend-analysis:view
					education:score:gradebook:export education:score:class-analysis:export education:score:grade-analysis:export
					education:score:subject-analysis:export education:score:trend-analysis:export
					""")),
					Set.copyOf(entries("""
					education:score:scheme:create education:score:scheme:update education:score:scheme:publish
					education:score:gradebook:create education:score:gradebook:update education:score:gradebook:submit
					education:score:gradebook:publish education:score:review education:score:makeup:manage
					education:score:gradebook:import education:score:change:request education:score:change:approve
					"""))),
			new Product("EDU_TEACHING_RESOURCES", "教学资源与教研", List.of(),
					entries("""
					/education/teaching /education/teaching-center/support /education/teaching-center/offerings /education/teaching-center/resources
					/education/teaching-center/preparations /education/teaching-center/preparation-invitations
					/education/teaching-center/preparation-members /education/teaching-center/coursewares /education/teaching-center/materials
					/education/teaching-center/question-banks /education/teaching-center/questions /education/teaching-center/knowledge-points
					/education/teaching-center/api/plans /education/teaching-center/api/lesson-plans
					/education/teaching-center/api/preparations /education/teaching-center/api/coursewares
					/education/teaching-center/api/materials /education/teaching-center/api/question-banks
					/education/teaching-center/api/questions /education/teaching-center/api/knowledge-points
					/education/teaching-center/api/research /education/teaching-center/research-groups
					/education/teaching-center/research-activities /education/teaching-center/research-teachers
					/education/teaching-center/research-access /education/teaching-center/research-results
					/education/teaching-center/research-activity-invitations
					"""),
					entries("""
					/admin/education/teaching-center /admin/education/teaching-center/plan
					/admin/education/teaching-center/lesson-plan /admin/education/teaching-center/preparation
					/admin/education/teaching-center/courseware /admin/education/teaching-center/material
					/admin/education/teaching-center/question-bank /admin/education/teaching-center/knowledge-point
					/admin/education/teaching-center/research /portal/education/teaching-center
					/portal/education/teaching/plans /portal/education/teaching/lessons
					/portal/education/teaching/preparations /portal/education/teaching/courseware
					/portal/education/teaching/materials /portal/education/teaching/questions
					/portal/education/teaching/knowledge-points /portal/education/teaching/research
					"""),
					Set.copyOf(entries("""
					education:teaching:view education:teaching:plan:view education:teaching:lesson:view
					education:teaching:preparation:view education:teaching:research:view
					""")),
					Set.copyOf(entries("""
					education:teaching:manage education:teaching:plan:submit education:teaching:lesson:submit education:teaching:review
					"""))),
			new Product("EDU_HOMEWORK", "作业与学情", List.of(),
					entries("""
					/education/teaching-center/api/homeworks /education/teaching-center/api/homework-submissions
					/education/teaching-center/homework-support
					"""),
					entries("""
					/admin/education/teaching-center/homework /admin/education/teaching-center/homework-analysis
					/admin/education/teaching-center/error-book /portal/education/homework
					/portal/education/teaching/mistakes
					"""),
					Set.copyOf(entries("""
					education:homework:view education:homework:submission:view education:homework:analysis:view
					""")),
					Set.copyOf(entries("""
					education:homework:manage
					"""))),
			new Product("EDU_SUPERVISION", "教学督导", List.of(),
					entries("""
					/admin/education/supervision /portal/education/supervision
					"""),
					entries("""
					/admin/education/supervision /portal/education/supervision /portal/education/supervision/teaching
					"""),
					Set.copyOf(entries("""
					education:supervision:assignment:view education:supervision:plan:view education:supervision:record:view
					""")),
					Set.copyOf(entries("""
					education:supervision:plan:create education:supervision:plan:publish education:supervision:plan:update
					education:supervision:assignment:create education:supervision:issue:create education:supervision:review:create
					"""))),
			new Product("EDU_HOME_SCHOOL", "家校与班级协同", List.of(),
					entries("""
					/admin/education/home-school
					/admin/education/parent-meetings /portal/education/family/children /portal/education/family/notices
					/portal/education/family/feedback /portal/education/family/communications
					/portal/education/class-notices /portal/education/head-teacher /portal/education/parent-meetings
					"""),
					entries("""
					/admin/education/parents /admin/education/home-school/notices /admin/education/home-school/class-groups
					/admin/education/home-school/feedback /admin/education/home-school/communications
					/admin/education/parent-meetings /portal/education/family /portal/education/family/feedback
					/portal/education/family/communications /portal/education/family/meetings
					/portal/education/class-notices /portal/education/head-teacher
					"""),
					Set.copyOf(entries("""
					education:home-school:group:view education:home-school:communication:view
					education:home-school:feedback:view education:home-school:parent:view education:home-school:notice:view
					""")),
					Set.copyOf(entries("""
					education:home-school:group:create education:home-school:group:update education:home-school:group:manage
					education:home-school:parent:manage education:home-school:parent:create education:home-school:parent:update
					education:home-school:notice:create education:home-school:notice:update education:home-school:guardian:review
					education:home-school:feedback:update education:home-school:communication:create
					education:home-school:communication:export education:parent-meeting:manage
					"""))),
			new Product("CAMPUS_PUBLICATIONS", "通知公告", List.of(),
					List.of("/admin/publications", "/publications"),
					List.of("/admin/publications", "/portal/publications"),
					Set.of("message:publication:view", "message:publication:read"),
					Set.of("message:publication:manage")),
			new Product("COLLABORATION_OFFICE", "协同办公与流程配置", List.of(),
					entries("""
					/portal/collaboration/resources /portal/collaboration/requests /portal/collaboration/documents
					/portal/collaboration/contacts /portal/collaboration/departments /portal/collaboration/admin/resources
					/portal/collaboration/admin/requests /portal/collaboration/business-trips
					/admin/collaboration/business-trip/config /portal/education/leaves /portal/education/leave-balances
					/admin/education/leaves /admin/education/leave-balances /admin/workflows /admin/workflow-nodes
					/admin/workflow-edges /admin/workflow-acls /admin/workflow-outbox /admin/workflow-incidents
					/admin/forms /admin/form-fields /workflows /workflow-instances /workflow-tasks /workflow-notifications
					/workflow-delegations /workflow-directory/users /admin/workflow-executors /admin/workflow-instances
					"""),
					entries("""
					/admin/workflows /admin/workflow/workflow-nodes /admin/workflow/forms /admin/workflow/outbox
					/admin/workflow/incidents /admin/education/leaves /portal/collaboration
					/portal/collaboration/business-trips /portal/collaboration/documents /portal/education/leaves
					/portal/workflows /portal/tasks /portal/workflow-notifications /portal/workflow-delegations
					"""),
					Set.copyOf(entries("""
					workflow:definition:view workflow:monitor:view workflow:instance:view
					""")),
					Set.copyOf(entries("""
					workflow:definition:create workflow:definition:update workflow:definition:delete workflow:definition:publish
					workflow:form:manage workflow:instance:start workflow:instance:withdraw workflow:task:claim workflow:task:approve workflow:task:reject collaboration:office:apply collaboration:office:manage
					"""))),
			new Product("SHARED_DOCUMENTS", "共享文档", List.of(),
					List.of("/portal/collaboration/files/shares"),
					List.of("/portal/collaboration/files"),
					Set.of("education:shared-document:read"),
					Set.of("education:shared-document:write")),
			planned("ASSET_MANAGEMENT", "资产管理", "资产入库、调拨、报废和设计需求审批已建立。补齐定时盘点、移动扫码复核、仅本资产维保工单和单售授权验收。",
					"""
					/admin/facility/governance/assets /admin/facility/governance/preventive-plans /admin/facility/asset-lifecycle
					""",
					"""
					/admin/facility/assets
					""",
					"""
					facility:asset:view
					""",
					"""
					facility:asset:manage facility:asset-lifecycle:review
					"""),
			new Product("AI_KNOWLEDGE", "AI 知识库", List.of(),
					entries("""
					/admin/knowledge-bases /admin/knowledge-documents /admin/knowledge-search /admin/knowledge-index-tasks
					/education/ai/assistant/ask GET:/ai-model/models/options
					"""),
					List.of("/admin/education/knowledge"),
					Set.of("education:ai:knowledge:view"),
					Set.copyOf(entries("""
					education:ai:knowledge:manage education:ai:knowledge:import education:ai:knowledge:delete
					education:ai:assistant:use
					"""))),
			new Product("EDU_OPERATIONS_AGENT", "学期运营智能体", List.of(),
					List.of("/agent/operations"), List.of(),
					Set.of("education:operation-agent:view"),
					Set.of("education:operation-agent:execute", "education:operation-agent:manage",
							"education:operation-agent:review")),
			new Product("EDU_ANALYTICS", "校务数据分析", List.of(),
					List.of("/admin/education/data-center/school-rollups"),
					List.of("/admin/education/data-center"),
					Set.of("education:data-center:view"),
					Set.of("education:data-center:import")),
			new Product("THIRD_PARTY_INTEGRATIONS", "第三方集成管理", List.of(),
					List.of("/admin/integrations"),
					List.of("/admin/integration"),
					Set.copyOf(entries("""
					integration:connector:view integration:job:view integration:run:view integration:mapping:view
					integration:dead-letter:view integration:error:view
					""")),
					Set.copyOf(entries("""
					integration:connector:manage integration:connector:test integration:job:manage integration:job:run
					integration:dry-run integration:mapping:manage integration:reconcile integration:dead-letter:replay
					"""))));

	private static Product planned(String code, String name, String reason, String endpoints, String menus,
			String readPermissions, String writePermissions) {
		return new Product(code, name, List.of(), entries(endpoints), entries(menus),
				Set.copyOf(entries(readPermissions)), Set.copyOf(entries(writePermissions)), reason);
	}

	private static List<String> entries(String values) {
		return values.isBlank() ? List.of() : List.of(values.strip().split("\\s+"));
	}

	static Product require(String code) {
		return PRODUCTS.stream().filter(item -> item.code().equals(code)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("未登记的模块：" + code));
	}

	static Set<String> resolve(Collection<String> requested) {
		if (requested == null || requested.isEmpty()) {
			throw new IllegalArgumentException("至少选择一个业务模块");
		}
		Set<String> result = new TreeSet<>();
		for (String code : requested) {
			add(result, code);
		}
		return result;
	}

	private static void add(Set<String> result, String code) {
		Product product = require(code);
		if (!product.issuable()) {
			throw new IllegalArgumentException("产品暂不可签发：" + product.name() + "，" + product.pendingReason());
		}
		if (result.add(code)) {
			product.dependencies().forEach(parent -> add(result, parent));
		}
	}

	static boolean matches(String path, String prefix) {
		return path.equals(prefix) || path.startsWith(prefix + "/");
	}
}

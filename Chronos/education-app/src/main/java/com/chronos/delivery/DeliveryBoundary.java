package com.chronos.delivery;

import com.chronos.commons.model.ResultData;
import com.chronos.education.operationagent.service.OperationAgentToolLicense;
import com.chronos.education.scheduling.service.CollaborationManagedFileAccessPolicy;
import com.chronos.model.vo.MenuVO;
import com.chronos.security.IamAuthorization;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import com.chronos.education.scheduling.service.ExamCourseIntegrationPolicy;
import com.chronos.education.grade.service.GradeCenterService;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.util.ServletRequestPathUtils;

@Configuration(proxyBeanMethods = false)
class DeliveryBoundary implements WebMvcConfigurer {
	private static final List<String> WORKFLOW_CONFIGURATION = List.of("/admin/workflows", "/admin/workflow-nodes",
			"/admin/workflow-edges", "/admin/workflow-acls", "/admin/forms", "/admin/form-fields");
	private static final List<String> FOUNDATION = List.of("/auth/login", "/auth/refresh", "/auth/revoke",
			"/auth/navigation", "/auth/account/change-password", "/auth/mfa", "/public/industry/context",
			"/delivery", "/actuator/health", "/admin/organizations/options", "/error");
	private final CustomerLicense license;
	private final JdbcTemplate jdbc;
	private static final Pattern MANAGED_FILE = Pattern.compile("/files/([a-zA-Z0-9-]{1,64})(?:/content)?");
	private static final String SHARES = "/portal/collaboration/files/shares";
	private static final String SHARE_READ = "education:shared-document:read";
	private static final String SHARE_WRITE = "education:shared-document:write";
	private static final Set<String> COLLABORATION_FLOWS = Set.of(
			"STAFF_BUSINESS_TRIP_APPROVAL", "STAFF_VEHICLE_USE_APPROVAL", "STAFF_SEAL_USE_APPROVAL",
			"OFFICIAL_DOCUMENT_ISSUE_APPROVAL", "EDU_STUDENT_LEAVE_APPROVAL", "EDU_TEACHER_LEAVE_APPROVAL");
	private final IamAuthorization authorization;
	private final CollaborationManagedFileAccessPolicy collaborationFiles;

	DeliveryBoundary(CustomerLicense license, JdbcTemplate jdbc, IamAuthorization authorization,
			CollaborationManagedFileAccessPolicy collaborationFiles) {
		this.license = license;
		this.jdbc = jdbc;
		this.authorization = authorization;
		this.collaborationFiles = collaborationFiles;
	}

	@Bean
	ExamCourseIntegrationPolicy examCourseIntegration() {
		return () -> !license.customer() || license.moduleAllowed("EDU_TEACHING_OPERATIONS");
	}

	@Bean
	GradeCenterService.ExamSourcePolicy examGradeSourcePolicy() {
		return this::examGradeIntegration;
	}

	@Bean
	com.chronos.education.scheduling.service.TeachingReviewService.DirectReviewPolicy teachingProductReview() {
		return () -> license.customer() && license.moduleAllowed("EDU_TEACHING_RESOURCES");
	}

	@Bean
	com.chronos.education.scheduling.service.HomeworkService.TeachingReferencePolicy homeworkTeachingReferences() {
		return () -> !license.customer() || license.moduleAllowed("EDU_TEACHING_RESOURCES");
	}

	@Bean
	com.chronos.education.parentmeeting.controller.ParentMeetingController.RoomIntegrationPolicy parentMeetingRooms() {
		return () -> !license.customer() || license.moduleAllowed("CAMPUS_MEETINGS");
	}

	@Bean
	com.chronos.workflow.WorkflowStartValidator teacherPersonnelLicense() {
		return new com.chronos.workflow.WorkflowStartValidator() {
			@Override
			public boolean supports(String flowCode) {
				return flowCode != null && flowCode.startsWith(
						com.chronos.education.scheduling.service.TeacherPersonnelWorkflowService.PREFIX);
			}

			@Override
			public void validate(String actor, java.util.Map<String, Object> formData) {
				if (license.customer() && !license.moduleAllowed("EDU_TEACHER_PERSONNEL")) {
					throw new AccessDeniedException("当前部署未开通教师人事");
				}
			}
		};
	}

	@Bean
	com.chronos.workflow.WorkflowStartValidator collaborationWorkflowLicense() {
		return new com.chronos.workflow.WorkflowStartValidator() {
			@Override
			public boolean supports(String flowCode) {
				return flowCode != null && COLLABORATION_FLOWS.contains(flowCode);
			}

			@Override
			public void validate(String actor, Map<String, Object> formData) {
				if (license.customer() && !license.moduleAllowed("COLLABORATION_OFFICE")) {
					throw new AccessDeniedException("当前部署未开通协同办公");
				}
			}
		};
	}

	@Bean
	com.chronos.workflow.WorkflowProductAccess workflowProductAccess() {
		return new com.chronos.workflow.WorkflowProductAccess() {
			@Override
			public boolean allows(String flowCode) {
				if (!license.customer() || flowCode == null) return true;
				if (COLLABORATION_FLOWS.contains(flowCode)) return license.moduleAllowed("COLLABORATION_OFFICE");
				if (flowCode.startsWith(com.chronos.education.scheduling.service.TeacherPersonnelWorkflowService.PREFIX)) {
					return license.moduleAllowed("EDU_TEACHER_PERSONNEL");
				}
				return switch (flowCode) {
					case "EDU_GRADEBOOK_REVIEW", "EDU_GRADE_CHANGE_REVIEW" -> license.moduleAllowed("EDU_GRADES");
					case "EDU_TEACHING_CONTENT_REVIEW" -> license.moduleAllowed("EDU_TEACHING_RESOURCES");
					case "EDU_CLASSROOM_RESERVATION_APPROVAL" -> license.moduleAllowed("CAMPUS_VENUE");
					case "EDU_COURSE_ADJUSTMENT_APPROVAL" -> license.moduleAllowed("EDU_TEACHING_OPERATIONS")
							&& license.moduleAllowed("COLLABORATION_OFFICE");
					default -> true;
				};
			}

			@Override
			public boolean canStart(String flowCode, String actor) {
				return !officeFlow(flowCode) || officeRole(actor, "MANAGE");
			}

			@Override
			public boolean startsInLauncher(String flowCode) {
				return !license.customer() || flowCode == null || !COLLABORATION_FLOWS.contains(flowCode);
			}

			@Override
			public boolean canViewInstance(String flowCode, String initiator, String actor) {
				if (!officeFlow(flowCode)) return true;
				if (initiator.equals(actor)) return officeRole(actor, "MANAGE") || officeRole(actor, "OFFICE_APPROVER");
				return sameOfficeSchool(initiator, actor) && officeRole(actor, "OFFICE_APPROVER");
			}

			@Override
			public boolean canHandle(String flowCode, String initiator, String actor) {
				if (!officeFlow(flowCode) || initiator.equals(actor)) return true;
				return sameOfficeSchool(initiator, actor) && officeRole(actor, "OFFICE_APPROVER");
			}

			@Override
			public boolean canDecide(String flowCode, String initiator, String reviewer) {
				return !officeFlow(flowCode) || !initiator.equals(reviewer)
						&& sameOfficeSchool(initiator, reviewer) && officeRole(reviewer, "OFFICE_APPROVER");
			}

			@Override
			public boolean canAssignApproval(String definitionId, String initiator, String reviewer) {
				if (!license.customer() || !license.moduleAllowed("COLLABORATION_OFFICE")) return true;
				var codes = jdbc.queryForList("SELECT flow_code FROM wf_definition WHERE id=?", String.class, definitionId);
				return codes.isEmpty() || canDecide(codes.getFirst(), initiator, reviewer);
			}

			@Override
			public List<String> defaultReviewers(String definitionId, String initiator, String configuredRole) {
				if (!license.customer() || !"EDU_ACADEMIC_APPROVER".equals(configuredRole)) return null;
				var codes = jdbc.queryForList("SELECT flow_code FROM wf_definition WHERE id=?", String.class, definitionId);
				if (codes.isEmpty() || !officeFlow(codes.getFirst())) return null;
				return jdbc.queryForList("""
						SELECT DISTINCT reviewer.username FROM t_admin_user applicant
						JOIN t_admin_user reviewer ON reviewer.organization_id=applicant.organization_id
						JOIN t_user_role ur ON ur.user_id=reviewer.id JOIN t_role r ON r.id=ur.role_id
						WHERE applicant.username=? AND reviewer.username<>applicant.username AND reviewer.status=1
						  AND r.status=1 AND r.role_code='DELIVERY_SCHOOL_COLLABORATION_OFFICE_OFFICE_APPROVER_' || applicant.organization_id
						ORDER BY reviewer.username
						""", String.class, initiator);
			}

			private boolean officeFlow(String code) {
				if (!license.customer() || code == null) return false;
				if (COLLABORATION_FLOWS.contains(code)) return true;
				if (!license.moduleAllowed("COLLABORATION_OFFICE")) return false;
				return Boolean.TRUE.equals(jdbc.queryForObject("""
						SELECT EXISTS(SELECT 1 FROM wf_definition d
						  JOIN t_admin_user u ON u.username=d.create_by
						  JOIN t_user_role ur ON ur.user_id=u.id JOIN t_role r ON r.id=ur.role_id
						  WHERE d.flow_code=? AND r.role_code=?)
						""", Boolean.class, code, CustomerDelegation.GROUP_ROLE));
			}

			@Override
			public boolean canConfigureTemplate(String flowCode, String actor) {
				if (!license.customer() || flowCode == null || !COLLABORATION_FLOWS.contains(flowCode) || !allows(flowCode)) return false;
				// The built-in templates are owned by SYSTEM. Customer template administrators
				// must be explicitly enrolled in the group authorization role.
				return Boolean.TRUE.equals(jdbc.queryForObject("""
						SELECT EXISTS(SELECT 1 FROM t_admin_user u JOIN t_user_role ur ON ur.user_id=u.id
						JOIN t_role r ON r.id=ur.role_id WHERE u.username=? AND r.role_code=? AND r.status=1)
						""", Boolean.class, actor, CustomerDelegation.GROUP_ROLE));
			}
		};
	}

	private boolean officeRole(String username, String role) {
		return Boolean.TRUE.equals(jdbc.queryForObject("""
				SELECT EXISTS(SELECT 1 FROM t_admin_user u JOIN t_user_role ur ON ur.user_id=u.id
				  JOIN t_role r ON r.id=ur.role_id WHERE u.username=? AND u.status=1 AND r.status=1
				  AND r.role_code='DELIVERY_SCHOOL_COLLABORATION_OFFICE_' || ? || '_' || u.organization_id)
				""", Boolean.class, username, role));
	}

	private boolean sameOfficeSchool(String owner, String actor) {
		return Boolean.TRUE.equals(jdbc.queryForObject("""
				SELECT EXISTS(SELECT 1 FROM t_admin_user a JOIN t_admin_user b ON a.organization_id=b.organization_id
				  WHERE a.username=? AND b.username=? AND b.status=1)
				""", Boolean.class, owner, actor));
	}

	@Bean
	OperationAgentToolLicense operationAgentToolLicense() {
		return code -> !license.customer()
				|| (code != null && license.moduleAllowed("EDU_OPERATIONS_AGENT") && licensedTool(code));
	}

	private boolean licensedTool(String code) {
		return switch (code) {
			// These three scoped actions form the standalone agent’s academic setup workflow.
			case "academic.term.create", "academic.calendar.prepare", "academic.bell.configure" -> true;
			case "knowledge.policy.search" -> license.moduleAllowed("AI_KNOWLEDGE");
			case "teacher.onboarding.request" -> license.moduleAllowed("EDU_TEACHER_PERSONNEL");
			case "term.end.exam.check" -> license.moduleAllowed("EDU_EXAM");
			case "term.end.grade.check" -> license.moduleAllowed("EDU_GRADES");
			case "term.graduation.prepare" -> license.moduleAllowed("EDU_STUDENT_LIFECYCLE");
			case "semester.context.check" -> license.moduleAllowed("TERM_CALENDAR")
					&& license.moduleAllowed("EDU_TEACHING_OPERATIONS");
			case "term.end.check", "term.end.archive.check" -> license.moduleAllowed("TERM_CALENDAR")
					&& license.moduleAllowed("EDU_STUDENT_LIFECYCLE") && license.moduleAllowed("EDU_GRADES")
					&& license.moduleAllowed("EDU_EXAM") && license.moduleAllowed("EDU_TEACHING_OPERATIONS");
			case "account.binding.check" -> license.moduleAllowed("EDU_TEACHER_PERSONNEL")
					&& license.moduleAllowed("EDU_STUDENT_LIFECYCLE");
			case "grade.exam.sync" -> license.moduleAllowed("EDU_GRADES") && examGradeIntegration();
			case "admission.accommodation.check", "admission.accommodation.resource.check",
					"admission.accommodation.resource.maintain", "admission.accommodation.plan",
					"admission.accommodation.assign.batch" ->
					license.moduleAllowed("EDU_STUDENT_LIFECYCLE") && license.moduleAllowed("EDU_ACCOMMODATION");
			case "admission.import.managed-file", "admission.data.check", "admission.class.assign.batch",
					"admission.review.batch", "admission.enroll.batch", "student.account.provision.batch" ->
					license.moduleAllowed("EDU_STUDENT_LIFECYCLE");
			case "teaching.assignment.generate", "teaching.offering.generate", "teaching.readiness.check",
					"teaching.suspension.preview", "scheduling.generate.full", "scheduling.submit.best",
					"scheduling.review.submitted", "scheduling.apply.approved", "scheduling.publish" ->
					license.moduleAllowed("EDU_TEACHING_OPERATIONS");
			case "exam.plan.upsert", "exam.sessions.create", "exam.rooms.create", "exam.candidates.assign",
					"exam.invigilation.auto", "exam.conflicts.check", "exam.plan.publish" ->
					license.moduleAllowed("EDU_EXAM");
			case "grade.scheme.upsert", "grade.gradebook.create", "grade.items.save", "grade.submit",
					"grade.review.check", "grade.review", "grade.publish", "grade.change.request",
					"grade.change.review" -> license.moduleAllowed("EDU_GRADES");
			// A new tool requires an explicit link to its licensed business products.
			default -> false;
		};
	}

	private boolean examGradeIntegration() {
		return license.moduleAllowed("EDU_EXAM") && license.moduleAllowed("EDU_TEACHING_OPERATIONS")
				&& license.moduleAllowed("EDU_TEACHING_RESOURCES");
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new HandlerInterceptor() {
			@Override
			public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
				String path = ServletRequestPathUtils.getParsedRequestPath(request).pathWithinApplication().value();
				if (path.startsWith("/api/")) path = path.substring(4);
				if (!license.customer()) {
					if (!license.issuer() && DeliveryCatalog.matches(path, "/admin/sales-delivery")) {
						response.setStatus(HttpServletResponse.SC_NOT_FOUND);
						return false;
					}
					return true;
				}
				String actual = path;
				if (!("GET".equals(request.getMethod()) && "/ai-model/models/options".equals(actual))
						&& (DeliveryCatalog.matches(actual, "/ai-model/models")
								|| DeliveryCatalog.matches(actual, "/ai-model/usage"))) {
					var actor = SecurityContextHolder.getContext().getAuthentication();
					if (!license.modelAdministrationAllowed() || !deploymentAdministrator(actor)) {
						throw new AccessDeniedException("AI 模型和用量策略由集团交付管理员维护");
					}
					return true;
				}
				if (WORKFLOW_CONFIGURATION.stream().anyMatch(entry -> DeliveryCatalog.matches(actual, entry))
						&& !deploymentAdministrator(SecurityContextHolder.getContext().getAuthentication())) {
					throw new AccessDeniedException("流程与表单模板由集团交付管理员配置");
				}
				if (DeliveryCatalog.matches(actual, "/admin/integrations")) {
					var actor = SecurityContextHolder.getContext().getAuthentication();
					if (!deploymentAdministrator(actor)) {
						throw new AccessDeniedException("集成连接器由集团交付管理员维护");
					}
				}
				if ("/portal/education/family/grades".equals(actual)) {
					if ("GET".equals(request.getMethod()) && license.moduleAllowed("EDU_HOME_SCHOOL")
							&& license.moduleAllowed("EDU_GRADES")) return true;
					throw new AccessDeniedException("家长成绩查询需要同时开通家校与班级协同和成绩管理");
				}
				if ((DeliveryCatalog.matches(actual, "/admin/education/schedule-pending-substitutions")
						|| DeliveryCatalog.matches(actual, "/admin/education/course-adjustment-incidents"))
						&& !license.moduleAllowed("COLLABORATION_OFFICE")) {
					throw new AccessDeniedException("课务审批联动需要开通协同办公");
				}
				if ((("/admin/education/supervision/assignments".equals(actual) && "POST".equals(request.getMethod()))
						|| actual.matches("/admin/education/supervision/plans/[^/]+/assignment-offerings(?:/.*)?"))
						&& !license.moduleAllowed("EDU_TEACHING_OPERATIONS")) {
					throw new AccessDeniedException("当前部署可使用独立督导课次，课表联动须开通教学运行");
				}
				if (DeliveryCatalog.matches(actual, SHARES)) {
					var actor = SecurityContextHolder.getContext().getAuthentication();
					if (!license.moduleAllowed("SHARED_DOCUMENTS") || !authorization.has(actor,
							"GET".equals(request.getMethod()) ? SHARE_READ : SHARE_WRITE)) {
						throw new AccessDeniedException("无权操作共享文档");
					}
					return true;
				}
				if (DeliveryCatalog.matches(actual, "/portal/education/classroom-reservations")
						|| actual.equals("/portal/education/classrooms/options")) {
					var actor = SecurityContextHolder.getContext().getAuthentication();
					if (!license.moduleAllowed("CAMPUS_VENUE") || !authorization.any(actor,
							"education:venue-reservation:apply", "education:resource:venue:manage")) {
						throw new AccessDeniedException("无权使用教室预约");
					}
				}
				if (DeliveryCatalog.matches(actual, "/admin/education/exam")) {
					if ((actual.contains("/suspension-") || actual.endsWith("/offerings"))
							&& !license.moduleAllowed("EDU_TEACHING_OPERATIONS")) {
						throw new AccessDeniedException("当前部署未开通教学运行联动");
					}
					if ((actual.contains("/paper-") || actual.contains("/scores/"))
							&& !(license.moduleAllowed("EDU_TEACHING_OPERATIONS")
									&& license.moduleAllowed("EDU_TEACHING_RESOURCES")
									&& license.moduleAllowed("EDU_GRADES"))) {
						throw new AccessDeniedException("当前部署未开通逐题成绩联动");
					}
				}
				if ((actual.endsWith("/exam-sessions") && DeliveryCatalog.matches(actual, "/admin/education/grades/offerings"))
						|| (actual.endsWith("/sync-exam-scores") && DeliveryCatalog.matches(actual, "/admin/education/grades/gradebooks"))
						|| DeliveryCatalog.matches(actual, "/admin/education/grade-analysis/knowledge")) {
					if (!examGradeIntegration()) {
						throw new AccessDeniedException("当前部署未开通考务逐题成绩联动");
					}
				}
				if (license.moduleAllowed("COLLABORATION_OFFICE") && officeApplicationWrite(request, actual)
						&& !authorization.has(SecurityContextHolder.getContext().getAuthentication(), "collaboration:office:apply")) {
					throw new AccessDeniedException("当前账号没有办公申请权限");
				}
				if (license.moduleAllowed("COLLABORATION_OFFICE") && officeRawStart(request, actual)) {
					throw new AccessDeniedException("请从协同办公的对应业务入口发起");
				}
				if (FOUNDATION.stream().anyMatch(entry -> DeliveryCatalog.matches(actual, entry))
							|| license.products().stream().anyMatch(product -> product.allows(request.getMethod(), actual))
							|| (license.moduleAllowed("CAMPUS_MEETINGS") && meetingFile(request, actual))
							|| (license.moduleAllowed("COLLABORATION_OFFICE") && officeFile(request, actual))
							|| (license.moduleAllowed("SHARED_DOCUMENTS") && sharedFile(request, actual))) return true;
				throw new AccessDeniedException("该功能未在本部署开通");
			}
		});
	}

	private boolean deploymentAdministrator(org.springframework.security.core.Authentication actor) {
		return DeliveryAccessController.owner(actor) || (actor != null && actor.getAuthorities().stream()
				.anyMatch(grant -> ("ROLE_CODE_" + CustomerDelegation.GROUP_ROLE).equals(grant.getAuthority())));
	}

	private boolean sharedFile(HttpServletRequest request, String path) {
		var actor = SecurityContextHolder.getContext().getAuthentication();
		String businessType = CollaborationManagedFileAccessPolicy.SHARE_TYPE;
		if ("POST".equals(request.getMethod()) && "/files".equals(path)) {
			String share = request.getParameter("businessId");
			return businessType.equals(request.getParameter("businessType")) && share != null && !share.isBlank()
					&& authorization.has(actor, SHARE_WRITE) && collaborationFiles.canWrite(actor.getName(), businessType, share);
		}
		var match = MANAGED_FILE.matcher(path);
		if (!"GET".equals(request.getMethod()) || !match.matches() || !authorization.has(actor, SHARE_READ)) {
			return false;
		}
		// A document is downloadable only after the owner attaches it to the corresponding share.
		var shares = jdbc.queryForList("""
				SELECT s.id FROM t_managed_file f JOIN collab_file_share s ON s.id=f.business_id AND s.file_id=f.id
				WHERE f.id=? AND f.business_type=? AND f.status='ACTIVE' AND s.status='ACTIVE'
				""", String.class, match.group(1), businessType);
		return !shares.isEmpty() && collaborationFiles.canRead(actor.getName(), businessType, shares.getFirst());
	}

	private boolean officeApplicationWrite(HttpServletRequest request, String path) {
		if ("GET".equals(request.getMethod())) return false;
		if (path.endsWith("/complete")) return false;
		return List.of("/portal/collaboration/requests", "/portal/collaboration/documents",
				"/portal/collaboration/business-trips", "/portal/education/leaves")
				.stream().anyMatch(entry -> DeliveryCatalog.matches(path, entry));
	}

	private boolean officeRawStart(HttpServletRequest request, String path) {
		if (!"POST".equals(request.getMethod()) || !path.matches("/workflows/[^/]+/start")) return false;
		String id = path.split("/")[2];
		return jdbc.queryForList("SELECT flow_code FROM wf_definition WHERE id=?", String.class, id)
				.stream().anyMatch(COLLABORATION_FLOWS::contains);
	}

	private boolean officeFile(HttpServletRequest request, String path) {
		var actor = SecurityContextHolder.getContext().getAuthentication();
		if (actor == null || !authorization.any(actor, "workflow:instance:view", "workflow:form:manage")) return false;
		if ("POST".equals(request.getMethod()) && "/files".equals(path)) {
			if (!authorization.has(actor, "collaboration:office:apply")) return false;
			String type = request.getParameter("businessType");
			String id = request.getParameter("businessId");
			if (id == null) return "WORKFLOW_FORM_DRAFT".equals(type) || type == null;
			return CollaborationManagedFileAccessPolicy.DOCUMENT_TYPE.equals(type)
					&& collaborationFiles.canWrite(actor.getName(), type, id);
		}
		var match = MANAGED_FILE.matcher(path);
		if (!"GET".equals(request.getMethod()) || !match.matches()) return false;
		return jdbc.queryForList("SELECT business_type FROM t_managed_file WHERE id=? AND status='ACTIVE'",
				String.class, match.group(1)).stream().anyMatch(Set.of("WORKFLOW_FORM_DRAFT", "WORKFLOW_FORM",
						CollaborationManagedFileAccessPolicy.DOCUMENT_TYPE,
						CollaborationManagedFileAccessPolicy.REQUEST_TYPE)::contains);
	}

	private boolean meetingFile(HttpServletRequest request, String path) {
		if ("POST".equals(request.getMethod()) && "/files".equals(path)) {
			// The existing meeting workflow binds the uploader's draft to its material record.
			String type = request.getParameter("businessType");
			return (type == null || "WORKFLOW_FORM_DRAFT".equals(type)) && request.getParameter("businessId") == null;
		}
		var match = MANAGED_FILE.matcher(path);
		return "GET".equals(request.getMethod()) && match.matches() && Boolean.TRUE.equals(jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM t_managed_file WHERE id=? AND business_type='EDUCATION_MEETING')
				""", Boolean.class, match.group(1)));
	}
}

@ControllerAdvice(assignableTypes = com.chronos.controller.AuthController.class)
class DeliveryNavigation implements ResponseBodyAdvice<Object> {
	private final CustomerLicense license;

	DeliveryNavigation(CustomerLicense license) {
		this.license = license;
	}

	@Override
	public boolean supports(MethodParameter method, Class<? extends HttpMessageConverter<?>> converter) {
		return true;
	}

	@Override
	public Object beforeBodyWrite(Object body, MethodParameter method, MediaType type,
			Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request, ServerHttpResponse response) {
		if (!(body instanceof ResultData<?> result) || !"200".equals(result.getCode())) return body;
		if (result.getData() instanceof Map<?, ?> data && data.get("menus") instanceof List<?> menus) {
			Map<Object, Object> visible = new LinkedHashMap<>(data);
			visible.put("menus", filter(menus));
			visible.put("delivery", license.status());
			return SalesDeliveryController.ok(visible);
		}
		if ("navigation".equals(method.getMethod().getName()) && result.getData() instanceof List<?> menus) {
			return SalesDeliveryController.ok(filter(menus));
		}
		return body;
	}

	private List<MenuVO> filter(List<?> data) {
		return data.stream().filter(MenuVO.class::isInstance).map(MenuVO.class::cast).filter(menu -> {
			String path = menu.getPath();
			var children = menu.getChildren() == null ? List.<MenuVO>of() : filter(menu.getChildren());
			menu.setChildren(children);
			if ("/admin/sales-delivery".equals(path)) return license.issuer();
			if ("/admin/delivery-access".equals(path)) return license.customer();
			if (!license.customer()) return true;
			return !children.isEmpty() || (path != null && license.status().routes().contains(path));
		}).toList();
	}
}

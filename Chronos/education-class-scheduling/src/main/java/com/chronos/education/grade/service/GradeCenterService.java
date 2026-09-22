package com.chronos.education.grade.service;

import com.chronos.education.grade.dao.*;
import com.chronos.education.grade.dto.GradeDtos.*;
import com.chronos.education.grade.model.*;
import com.chronos.education.scheduling.dao.*;
import com.chronos.education.scheduling.model.*;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.workflow.WorkflowService;
import com.chronos.Idao.workflow.IWorkflowTaskRepository;
import com.chronos.Idao.workflow.IWorkflowInstanceRepository;
import com.chronos.model.workflow.WorkflowTask;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import org.springframework.security.access.AccessDeniedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal; import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.time.LocalDateTime; import java.util.*; import java.util.stream.Collectors;

/** 成绩中心第一交付切片：仅允许 MANUAL 成绩项目，所有状态转换在事务内完成。 */
@Service
public class GradeCenterService {
 private final AssessmentSchemeRepository schemes; private final AssessmentComponentRepository components; private final GradebookRepository gradebooks; private final GradebookStudentRepository students; private final GradeItemRepository items; private final CourseGradeRepository courseGrades; private final GradePublishSnapshotRepository snapshots; private final CourseOfferingRepository offerings; private final TeachingClassMemberRepository members; private final StudentProfileRepository profiles; private final WorkflowService workflows; private final IWorkflowTaskRepository workflowTasks; private final IWorkflowInstanceRepository workflowInstances; private final EducationDataScopeService dataScopes; private final GradeNotificationService notifications; private final IAuditLogService audit; private final ObjectMapper json; private final DomainEventOutboxService domainEvents; private final GradeRuleEvaluationService gradeRules;
 public GradeCenterService(AssessmentSchemeRepository schemes,AssessmentComponentRepository components,GradebookRepository gradebooks,GradebookStudentRepository students,GradeItemRepository items,CourseGradeRepository courseGrades,GradePublishSnapshotRepository snapshots,CourseOfferingRepository offerings,TeachingClassMemberRepository members,StudentProfileRepository profiles,WorkflowService workflows,IWorkflowTaskRepository workflowTasks,IWorkflowInstanceRepository workflowInstances,EducationDataScopeService dataScopes,GradeNotificationService notifications,IAuditLogService audit,ObjectMapper json,DomainEventOutboxService domainEvents,GradeRuleEvaluationService gradeRules){this.schemes=schemes;this.components=components;this.gradebooks=gradebooks;this.students=students;this.items=items;this.courseGrades=courseGrades;this.snapshots=snapshots;this.offerings=offerings;this.members=members;this.profiles=profiles;this.workflows=workflows;this.workflowTasks=workflowTasks;this.workflowInstances=workflowInstances;this.dataScopes=dataScopes;this.notifications=notifications;this.audit=audit;this.json=json;this.domainEvents=domainEvents;this.gradeRules=gradeRules;}
	@Transactional(readOnly = true)
	public List<AssessmentScheme> listSchemes(String offeringId, String actor) {
		var scope = dataScopes.resolve(actor);
		List<AssessmentScheme> values = offeringId == null
				? schemes.findAll()
				: schemes.findByOfferingIdOrderByCreateTimeDesc(offeringId);
		return values.stream()
				.filter(value -> scope.fullAccess()
						|| offerings.findById(value.getOfferingId())
								.map(offering -> scope.teacherIds().contains(offering.getTeacherId()))
								.orElse(false))
				.toList();
	}

	@Transactional(readOnly = true)
	public SchemeDetailResponse getScheme(String id, String actor) {
		AssessmentScheme scheme = schemes.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("考核方案不存在"));
		authorizedOffering(scheme.getOfferingId(), actor);
		List<ComponentDetail> details = components.findBySchemeIdOrderBySortOrder(id).stream()
				.map(component -> new ComponentDetail(
						component.getId(),
						component.getCode(),
						component.getName(),
						component.getSourceType(),
						component.getWeight(),
						component.getMaxScore(),
						component.getSortOrder()))
				.toList();
		return new SchemeDetailResponse(
				scheme.getId(),
				scheme.getOfferingId(),
				scheme.getName(),
				scheme.getTotalScore(),
				scheme.getPassScore(),
				scheme.getStatus(),
				scheme.getPublishedVersionNo(),
				scheme.getRowVersion(),
				details);
	}

	@Transactional(readOnly = true)
	public List<OfferingOption> availableOfferings(String actor) {
		var scope = dataScopes.resolve(actor);
		return offerings.findAll().stream()
				.filter(offering -> "ACTIVE".equals(offering.getStatus()))
				.filter(offering -> scope.fullAccess()
						|| scope.teacherIds().contains(offering.getTeacherId()))
				.map(offering -> new OfferingOption(
						offering.getId(),
						offering.getSemesterCode(),
						offering.getOfferingCode(),
						offering.getCourseName(),
						offering.getTeachingClassName(),
						offering.getTeacherName()))
				.sorted(Comparator
						.comparing(OfferingOption::semesterCode, Comparator.reverseOrder())
						.thenComparing(OfferingOption::courseName)
						.thenComparing(OfferingOption::teachingClassName))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<Gradebook> listGradebooks(String actor) {
		var scope = dataScopes.resolve(actor);
		if (scope.fullAccess()) {
			return gradebooks.findAll();
		}
		return scope.teacherIds().stream()
				.flatMap(id -> gradebooks.findByTeacherIdOrderByCreateTimeDesc(id).stream())
				.distinct()
				.toList();
	}
	@Transactional
	public AssessmentScheme createScheme(SchemeCommand command, String actor) {
		validateScheme(command);
		CourseOffering offering = authorizedOffering(command.offeringId(), actor);
		AssessmentScheme scheme = new AssessmentScheme();
		scheme.setSchoolId(serverSchoolId(actor));
		scheme.setOfferingId(offering.getId());
		scheme.setName(command.name().trim());
		scheme.setTotalScore(command.totalScore());
		scheme.setPassScore(command.passScore());
		scheme.setStatus("DRAFT");
		scheme = schemes.save(scheme);
		saveComponents(scheme.getId(), command.components());
		audit.log(actor, "EDU_GRADE_SCHEME_CREATE", "schemeId=" + scheme.getId());
		return scheme;
	}

	@Transactional
	public AssessmentScheme publishScheme(String id, String actor) {
		AssessmentScheme scheme = schemes.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("考核方案不存在"));
		authorizedOffering(scheme.getOfferingId(), actor);
		if ("PUBLISHED".equals(scheme.getStatus())) {
			return scheme;
		}
		if (!"DRAFT".equals(scheme.getStatus())) {
			throw new IllegalStateException("当前状态不能发布考核方案");
		}
		List<AssessmentComponent> values = components.findBySchemeIdOrderBySortOrder(id);
		if (values.isEmpty()) {
			throw new IllegalStateException("考核方案至少包含一个成绩项目");
		}
		validateWeights(values);
		scheme.setStatus("PUBLISHED");
		scheme.setPublishedVersionNo(1);
		audit.log(actor, "EDU_GRADE_SCHEME_PUBLISH", "schemeId=" + id);
		return schemes.save(scheme);
	}

	@Transactional
	public AssessmentScheme updateScheme(String id, SchemeCommand command, String actor) {
		AssessmentScheme scheme = schemes.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("考核方案不存在"));
		authorizedOffering(scheme.getOfferingId(), actor);
		if (!"DRAFT".equals(scheme.getStatus())) {
			throw new IllegalStateException("仅草稿方案可修改");
		}
		if (command.rowVersion() != null && !command.rowVersion().equals(scheme.getRowVersion())) {
			throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
					AssessmentScheme.class,
					id);
		}
		validateScheme(command);
		if (!scheme.getOfferingId().equals(command.offeringId())) {
			throw new IllegalArgumentException("考核方案创建后不能更换课程开设");
		}
		scheme.setName(command.name().trim());
		scheme.setTotalScore(command.totalScore());
		scheme.setPassScore(command.passScore());
		components.deleteBySchemeId(id);
		saveComponents(id, command.components());
		audit.log(actor, "EDU_GRADE_SCHEME_UPDATE", "schemeId=" + id);
		return schemes.save(scheme);
	}
 @Transactional public Gradebook createGradebook(GradebookCommand c,String actor){CourseOffering o=authorizedOffering(c.offeringId(),actor);AssessmentScheme s=schemes.findById(c.schemeId()).orElseThrow();if(!"PUBLISHED".equals(s.getStatus())||!s.getOfferingId().equals(o.getId()))throw new IllegalArgumentException("只能使用本课程已发布方案");if(gradebooks.findByOfferingIdAndSchemeId(c.offeringId(),c.schemeId()).isPresent())throw new IllegalStateException("该课程已有成绩册");Gradebook g=new Gradebook();g.setSchoolId(serverSchoolId(actor));g.setOfferingId(o.getId());g.setSchemeId(s.getId());g.setTeacherId(o.getTeacherId());g.setStatus("EDITING");g=gradebooks.save(g);for(TeachingClassMember m:members.findByOfferingIdAndEnrollmentStatus(o.getId(),"ENROLLED")){var p=profiles.findById(m.getStudentId()).orElse(null);if(p==null)continue;GradebookStudent gs=new GradebookStudent();gs.setGradebookId(g.getId());gs.setStudentId(p.getId());gs.setStudentNo(p.getStudentNo());gs.setStudentName(p.getStudentName());gs.setAdministrativeClassId(p.getAdministrativeClassId());gs.setEnrollmentStatus(m.getEnrollmentStatus());gs.setSourceMemberId(m.getId());gs.setEnrolledAt(m.getEnrolledAt());gs.setWithdrawnAt(m.getWithdrawnAt());gs.setSnapshotHash(hash(snapshotStudent(gs)));students.save(gs);}audit.log(actor,"EDU_GRADEBOOK_CREATE","gradebookId="+g.getId());return g;}
 @Transactional(readOnly=true) public GradebookDetailResponse getGradebook(String id,String actor){Gradebook g=authorizedGradebook(id,actor);List<StudentSnapshot> studentSnapshots=students.findByGradebookId(id).stream().map(s->new StudentSnapshot(s.getId(),s.getStudentId(),s.getStudentNo(),s.getStudentName(),s.getAdministrativeClassId(),s.getEnrollmentStatus(),s.getSourceMemberId(),s.getEnrolledAt(),s.getWithdrawnAt(),s.getSnapshotVersion(),s.getSnapshotHash())).toList();List<ComponentDetail> componentDetails=components.findBySchemeIdOrderBySortOrder(g.getSchemeId()).stream().map(c->new ComponentDetail(c.getId(),c.getCode(),c.getName(),c.getSourceType(),c.getWeight(),c.getMaxScore(),c.getSortOrder())).toList();List<GradeItemDetail> itemDetails=items.findByGradebookId(id).stream().map(i->new GradeItemDetail(i.getId(),i.getComponentId(),i.getStudentId(),i.getRawScore(),i.getConvertedScore(),i.getSpecialStatus(),i.getRemark(),i.getRowVersion())).toList();List<SnapshotMetadata> snapshotMetadata=snapshots.findByGradebookIdOrderByVersionNoDesc(id).stream().map(s->new SnapshotMetadata(s.getVersionNo(),s.getSnapshotHash(),s.getPublishedBy(),s.getPublishedAt())).toList();return new GradebookDetailResponse(g.getId(),g.getOfferingId(),g.getSchemeId(),g.getStatus(),g.getSubmissionNo(),g.getRowVersion(),g.getSubmittedAt(),g.getPublishedAt(),studentSnapshots,componentDetails,itemDetails,snapshotMetadata);}
 @Transactional(readOnly=true) public List<GradePublishSnapshot> snapshots(String id,String actor){authorizedGradebook(id,actor);return snapshots.findByGradebookIdOrderByVersionNoDesc(id);}
 @Transactional public Gradebook saveItems(String id,ItemsCommand command,String actor){Gradebook g=authorizedGradebook(id,actor);if(!Set.of("EDITING","REJECTED").contains(g.getStatus()))throw new IllegalStateException("当前状态不可录入");if(command.rowVersion()!=null&&!command.rowVersion().equals(g.getRowVersion()))throw new org.springframework.orm.ObjectOptimisticLockingFailureException(Gradebook.class,id);List<AssessmentComponent> allowed=components.findBySchemeIdOrderBySortOrder(g.getSchemeId());Map<String,AssessmentComponent> byId=allowed.stream().collect(Collectors.toMap(AssessmentComponent::getId,x->x));List<GradebookStudent> roster=students.findByGradebookId(id);for(GradeItemCommand c:command.items()){AssessmentComponent component=byId.get(c.componentId());if(component==null)throw new IllegalArgumentException("成绩项目不属于当前成绩册方案");if(c.rawScore()!=null&&(c.rawScore().signum()<0||c.rawScore().compareTo(component.getMaxScore())>0))throw new IllegalArgumentException("成绩超出项目满分");if(roster.stream().noneMatch(x->x.getStudentId().equals(c.studentId())))throw new IllegalArgumentException("学生不在成绩册快照");GradeItem item=items.findByGradebookId(id).stream().filter(x->x.getComponentId().equals(c.componentId())&&x.getStudentId().equals(c.studentId())).findFirst().orElseGet(GradeItem::new);item.setGradebookId(id);item.setComponentId(c.componentId());item.setStudentId(c.studentId());item.setRawScore(c.rawScore());item.setConvertedScore(c.rawScore());item.setSpecialStatus(c.specialStatus());item.setRemark(c.remark());items.save(item);}return gradebooks.saveAndFlush(g);}
 @Transactional public Gradebook submit(String id,String actor){Gradebook g=authorizedGradebook(id,actor);if(!Set.of("EDITING","REJECTED").contains(g.getStatus()))throw new IllegalStateException("成绩册不可提交");validateCompleteGradebook(g);g.setSubmissionNo(g.getSubmissionNo()+1);g.setSubmittedAt(LocalDateTime.now());g.setStatus("REVIEWING");var instance=workflows.startByCode("EDU_GRADEBOOK_REVIEW",id,Map.<String,Object>of("gradebookId",id,"submitter",actor),actor);g.setWorkflowInstanceId(instance.getId());audit.log(actor,"EDU_GRADEBOOK_SUBMIT","gradebookId="+id);return gradebooks.save(g);}
 @Transactional public Gradebook review(String id,String taskId,boolean approved,String comment,String actor){Gradebook g=gradebooks.findById(id).orElseThrow(()->new IllegalArgumentException("成绩册不存在"));WorkflowTask task=workflowTasks.findById(taskId).orElseThrow(()->new IllegalArgumentException("审核任务不存在"));if(!id.equals(workflows.instance(task.getInstanceId()).getBusinessKey()))throw new AccessDeniedException("审核任务不属于当前成绩册");if(dataScopes.resolve(actor).teacherIds().contains(g.getTeacherId()))throw new AccessDeniedException("提交人不得审核本人成绩册");var instance=approved?workflows.completeTask(taskId,true,comment,actor):workflows.rejectTask(taskId,"",comment,actor);if("COMPLETED".equals(instance.getStatus()))g.setStatus("APPROVED");else if("REJECTED".equals(instance.getStatus()))g.setStatus("REJECTED");else g.setStatus("REVIEWING");audit.log(actor,approved?"EDU_GRADEBOOK_APPROVE":"EDU_GRADEBOOK_REJECT","gradebookId="+id+",node="+task.getNodeKey());return gradebooks.save(g);}
 @Transactional public Gradebook publish(String id,String actor){Gradebook g=gradebooks.findById(id).orElseThrow(()->new IllegalArgumentException("成绩册不存在"));if("PUBLISHED".equals(g.getStatus()))return g;if(!"APPROVED".equals(g.getStatus()))throw new IllegalStateException("仅审核通过后可发布");List<GradebookStudent> ss=students.findByGradebookId(id);List<AssessmentComponent> cs=components.findBySchemeIdOrderBySortOrder(g.getSchemeId());List<GradeItem> allItems=items.findByGradebookId(id);Map<String,List<GradeItem>> by=allItems.stream().collect(Collectors.groupingBy(GradeItem::getStudentId));Map<String,AssessmentComponent> componentById=cs.stream().collect(Collectors.toMap(AssessmentComponent::getId,x->x));AssessmentScheme scheme=schemes.findById(g.getSchemeId()).orElseThrow();CourseOffering offering=offerings.findById(g.getOfferingId()).orElseThrow();int version=g.getSubmissionNo();Map<String,Object> snapshotData=new LinkedHashMap<>();snapshotData.put("snapshotVersion",1);snapshotData.put("capturedAt",LocalDateTime.now().toString());Map<String,Object> offeringSnapshot=new LinkedHashMap<>();offeringSnapshot.put("id",offering.getId());offeringSnapshot.put("offeringCode",offering.getOfferingCode());offeringSnapshot.put("teachingClassName",offering.getTeachingClassName());offeringSnapshot.put("semesterCode",offering.getSemesterCode());offeringSnapshot.put("courseCode",offering.getCourseCode());offeringSnapshot.put("courseName",offering.getCourseName());offeringSnapshot.put("campusId",offering.getCampusId());offeringSnapshot.put("offeringMode",offering.getOfferingMode());snapshotData.put("offering",offeringSnapshot);snapshotData.put("students",ss.stream().map(this::snapshotStudent).toList());snapshotData.put("components",cs.stream().map(this::snapshotComponent).toList());snapshotData.put("items",allItems.stream().map(this::snapshotItem).toList());String snapshot;try{snapshot=json.writeValueAsString(snapshotData);}catch(Exception e){throw new IllegalArgumentException("成绩快照序列化失败",e);}String hash=hash(snapshot);for(GradebookStudent student:ss){BigDecimal total=by.getOrDefault(student.getStudentId(),List.of()).stream().filter(x->x.getConvertedScore()!=null).map(x->{AssessmentComponent c=componentById.get(x.getComponentId());return c==null?BigDecimal.ZERO:x.getConvertedScore().divide(c.getMaxScore(),8,java.math.RoundingMode.HALF_UP).multiply(c.getWeight());}).reduce(BigDecimal.ZERO,BigDecimal::add);CourseGrade cg=new CourseGrade();cg.setGradebookId(id);cg.setStudentId(student.getStudentId());cg.setTotalScore(total);applyGradeLevel(cg,total);cg.setPassed(total.compareTo(scheme.getPassScore())>=0);cg.setVersionNo(version);cg.setSnapshotHash(hash);courseGrades.save(cg);notifications.published(id,student.getStudentId(),g.getOfferingId(),String.valueOf(version));}GradePublishSnapshot p=new GradePublishSnapshot();p.setGradebookId(id);p.setVersionNo(version);p.setSnapshotJson(snapshot);p.setSnapshotHash(hash);p.setPublishedBy(actor);p.setPublishedAt(LocalDateTime.now());snapshots.save(p);g.setStatus("PUBLISHED");g.setPublishedAt(LocalDateTime.now());audit.log(actor,"EDU_GRADEBOOK_PUBLISH","gradebookId="+id+",version="+version);Gradebook saved=gradebooks.save(g);domainEvents.enqueue(new com.chronos.education.grade.dto.GradeSourceEventContracts.CourseGradesPublishedV1(UUID.randomUUID().toString(),"CourseGradesPublishedV1",java.time.OffsetDateTime.now(),1,g.getId(),g.getOfferingId(),g.getSubmissionNo(),hash,actor));return saved;}
	@Transactional(readOnly = true)
	public List<CourseGrade> studentGrades(String studentId) {
		Map<String, CourseGrade> latest = new LinkedHashMap<>();
		courseGrades.findByStudentIdOrderByVersionNoDesc(studentId).stream()
				.filter(grade -> gradebooks.findById(grade.getGradebookId())
						.map(value -> "PUBLISHED".equals(value.getStatus()))
						.orElse(false))
				.forEach(grade -> latest.putIfAbsent(grade.getGradebookId(), grade));
		return List.copyOf(latest.values());
	}

	@Transactional(readOnly = true)
	public List<PortalGradeView> studentGradeViews(String studentId) {
		return studentGrades(studentId).stream().map(grade -> {
			Gradebook gradebook = gradebooks.findById(grade.getGradebookId()).orElseThrow();
			CourseOffering offering = offerings.findById(gradebook.getOfferingId()).orElseThrow();
			LocalDateTime publishedAt = snapshots.findByGradebookIdOrderByVersionNoDesc(gradebook.getId())
					.stream()
					.filter(value -> value.getVersionNo().equals(grade.getVersionNo()))
					.map(GradePublishSnapshot::getPublishedAt)
					.findFirst()
					.orElse(gradebook.getPublishedAt());
			return new PortalGradeView(
					grade.getId(), grade.getStudentId(), offering.getId(), offering.getSemesterCode(),
					offering.getCourseCode(), offering.getCourseName(), offering.getTeachingClassName(),
					grade.getTotalScore(), grade.getGradeLevel(), grade.getGradePoint(), grade.getPassed(),
					grade.getVersionNo(), publishedAt);
		}).toList();
	}
 @Transactional(readOnly=true) public CourseGrade studentGrade(String id,String studentId){return studentGrades(studentId).stream().filter(g->g.getId().equals(id)).findFirst().orElseThrow(()->new org.springframework.security.access.AccessDeniedException("成绩不存在或尚未发布"));}
	private CourseOffering authorizedOffering(String id, String actor) {
		CourseOffering offering = offerings.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("课程开设不存在"));
		var scope = dataScopes.resolve(actor);
		if (!scope.fullAccess() && !scope.teacherIds().contains(offering.getTeacherId())) {
			throw new AccessDeniedException("无权访问该课程开设");
		}
		return offering;
	}

	private Gradebook authorizedGradebook(String id, String actor) {
		Gradebook gradebook = gradebooks.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("成绩册不存在"));
		var scope = dataScopes.resolve(actor);
		if (!scope.fullAccess() && !scope.teacherIds().contains(gradebook.getTeacherId())) {
			throw new AccessDeniedException("无权访问该成绩册");
		}
		return gradebook;
	}
 private String serverSchoolId(String actor){return dataScopes.resolve(actor).campusIds().stream().findFirst().orElse("DEFAULT");}
	private void saveComponents(String schemeId, List<ComponentCommand> commands) {
		int defaultOrder = 0;
		for (ComponentCommand command : commands) {
			AssessmentComponent component = new AssessmentComponent();
			component.setSchemeId(schemeId);
			component.setCode(command.code().trim());
			component.setName(command.name().trim());
			component.setSourceType(command.sourceType());
			component.setWeight(command.weight());
			component.setMaxScore(command.maxScore());
			component.setSortOrder(command.sortOrder() == null ? defaultOrder++ : command.sortOrder());
			components.save(component);
		}
	}

	private void validateScheme(SchemeCommand command) {
		if (command == null
				|| command.offeringId() == null
				|| command.offeringId().isBlank()
				|| command.name() == null
				|| command.name().isBlank()) {
			throw new IllegalArgumentException("请填写方案名称和课程开设");
		}
		if (command.components() == null || command.components().isEmpty()) {
			throw new IllegalArgumentException("至少配置一个成绩项目");
		}
		if (command.totalScore() == null
				|| command.totalScore().signum() <= 0
				|| command.passScore() == null
				|| command.passScore().signum() < 0
				|| command.passScore().compareTo(command.totalScore()) > 0) {
			throw new IllegalArgumentException("分数范围无效");
		}
		Set<String> codes = new HashSet<>();
		List<AssessmentComponent> validationValues = new ArrayList<>();
		for (ComponentCommand value : command.components()) {
			if (value.code() == null
					|| value.code().isBlank()
					|| value.name() == null
					|| value.name().isBlank()) {
				throw new IllegalArgumentException("成绩项目编码和名称不能为空");
			}
			if (!codes.add(value.code().trim().toLowerCase(Locale.ROOT))) {
				throw new IllegalArgumentException("同一方案的成绩项目编码不能重复");
			}
			if (!"MANUAL".equals(value.sourceType())) {
				throw new IllegalArgumentException("当前仅支持人工录入成绩项目");
			}
			if (value.weight() == null
					|| value.weight().signum() < 0
					|| value.maxScore() == null
					|| value.maxScore().signum() <= 0) {
				throw new IllegalArgumentException("成绩项目权重和满分必须有效");
			}
			AssessmentComponent component = new AssessmentComponent();
			component.setWeight(value.weight());
			validationValues.add(component);
		}
		validateWeights(validationValues);
	}

	private void validateWeights(List<AssessmentComponent> values) {
		BigDecimal total = values.stream()
				.map(AssessmentComponent::getWeight)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		if (total.compareTo(new BigDecimal("100")) != 0) {
			throw new IllegalArgumentException("成绩项目权重合计必须为100");
		}
	}
	private Map<String,Object> snapshotStudent(GradebookStudent s){Map<String,Object> snapshot=new LinkedHashMap<>();snapshot.put("studentId",s.getStudentId());snapshot.put("studentNo",s.getStudentNo());snapshot.put("studentName",s.getStudentName());snapshot.put("administrativeClassId",s.getAdministrativeClassId());snapshot.put("enrollmentStatus",s.getEnrollmentStatus());snapshot.put("sourceMemberId",s.getSourceMemberId());snapshot.put("enrolledAt",s.getEnrolledAt());snapshot.put("withdrawnAt",s.getWithdrawnAt());return snapshot;}
	private Map<String, Object> snapshotComponent(AssessmentComponent component) {
		Map<String, Object> value = new LinkedHashMap<>();
		value.put("id", component.getId());
		value.put("code", component.getCode());
		value.put("name", component.getName());
		value.put("sourceType", component.getSourceType());
		value.put("maxScore", component.getMaxScore());
		value.put("weight", component.getWeight());
		return value;
	}

	private Map<String, Object> snapshotItem(GradeItem item) {
		Map<String, Object> value = new LinkedHashMap<>();
		value.put("componentId", item.getComponentId());
		value.put("studentId", item.getStudentId());
		value.put("rawScore", item.getRawScore());
		value.put("convertedScore", item.getConvertedScore());
		value.put("specialStatus", item.getSpecialStatus());
		value.put("remark", item.getRemark());
		return value;
	}
	private void applyGradeLevel(CourseGrade grade, BigDecimal score) {
		if (score.compareTo(new BigDecimal("90")) >= 0) {
			grade.setGradeLevel("A");
			grade.setGradePoint(new BigDecimal("4.0"));
		} else if (score.compareTo(new BigDecimal("80")) >= 0) {
			grade.setGradeLevel("B");
			grade.setGradePoint(new BigDecimal("3.0"));
		} else if (score.compareTo(new BigDecimal("70")) >= 0) {
			grade.setGradeLevel("C");
			grade.setGradePoint(new BigDecimal("2.0"));
		} else if (score.compareTo(new BigDecimal("60")) >= 0) {
			grade.setGradeLevel("D");
			grade.setGradePoint(new BigDecimal("1.0"));
		} else {
			grade.setGradeLevel("F");
			grade.setGradePoint(BigDecimal.ZERO);
		}
	}

	/** 每名快照学生的每个考核项目都必须有分数或特殊状态，禁止部分成绩进入审核。 */
	private void validateCompleteGradebook(Gradebook gradebook) {
		List<AssessmentComponent> requiredComponents = components
				.findBySchemeIdOrderBySortOrder(gradebook.getSchemeId());
		List<GradebookStudent> roster = students.findByGradebookId(gradebook.getId());
		Map<String, GradeItem> entered = items.findByGradebookId(gradebook.getId()).stream()
				.collect(Collectors.toMap(
						item -> item.getStudentId() + ":" + item.getComponentId(),
						item -> item));
		for (GradebookStudent student : roster) {
			for (AssessmentComponent component : requiredComponents) {
				GradeItem item = entered.get(student.getStudentId() + ":" + component.getId());
				if (item == null || (item.getRawScore() == null
						&& (item.getSpecialStatus() == null || item.getSpecialStatus().isBlank()))) {
					throw new IllegalArgumentException(
							"学生“" + student.getStudentName() + "”的“" + component.getName() + "”尚未录入");
				}
			}
		}
	}
 private String hash(Object value){try{String text=value instanceof String?(String)value:json.writeValueAsString(value);byte[] b=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte x:b)out.append(String.format("%02x",x));return out.toString();}catch(Exception e){throw new IllegalStateException("快照哈希失败",e);}}
}

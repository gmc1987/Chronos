package com.chronos.education.grade.service;

import com.chronos.education.grade.dao.CourseGradeRepository;
import com.chronos.education.grade.dao.GradebookRepository;
import com.chronos.education.grade.dao.GradebookStudentRepository;
import com.chronos.education.grade.dto.GradeAnalysisDtos.AnalysisFilters;
import com.chronos.education.grade.dto.GradeAnalysisDtos.AnalysisResponse;
import com.chronos.education.grade.dto.GradeAnalysisDtos.Distribution;
import com.chronos.education.grade.dto.GradeAnalysisDtos.FilterOption;
import com.chronos.education.grade.dto.GradeAnalysisDtos.GroupMetric;
import com.chronos.education.grade.dto.GradeAnalysisDtos.KnowledgeAnalysisResponse;
import com.chronos.education.grade.dto.GradeAnalysisDtos.Summary;
import com.chronos.education.grade.dto.GradeAnalysisDtos.TrendPoint;
import com.chronos.education.grade.model.CourseGrade;
import com.chronos.education.grade.model.Gradebook;
import com.chronos.education.grade.model.GradebookStudent;
import com.chronos.education.scheduling.dao.AdministrativeClassRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.EducationGradeRepository;
import com.chronos.education.scheduling.dao.ExamCandidateRepository;
import com.chronos.education.scheduling.dao.ExamItemScoreRepository;
import com.chronos.education.scheduling.dao.ExamPaperItemRepository;
import com.chronos.education.scheduling.dao.ExamPlanRepository;
import com.chronos.education.scheduling.dao.ExamSessionRepository;
import com.chronos.education.scheduling.dao.KnowledgePointRepository;
import com.chronos.education.scheduling.dao.QuestionKnowledgePointRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.SubjectRepository;
import com.chronos.education.scheduling.model.AdministrativeClass;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.EducationGrade;
import com.chronos.education.scheduling.model.ExamCandidate;
import com.chronos.education.scheduling.model.ExamPaperItem;
import com.chronos.education.scheduling.model.ExamPlan;
import com.chronos.education.scheduling.model.ExamSession;
import com.chronos.education.scheduling.model.KnowledgePoint;
import com.chronos.education.scheduling.model.StudentProfile;
import com.chronos.education.scheduling.model.Subject;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于已发布成绩事实生成实时分析。所有入口先应用教育数据范围，草稿和历史版本不会进入统计。
 */
@Service
@Transactional(readOnly = true)
public class GradeAnalysisService {
	private final CourseGradeRepository courseGrades;
	private final GradebookRepository gradebooks;
	private final GradebookStudentRepository students;
	private final CourseOfferingRepository offerings;
	private final AdministrativeClassRepository classes;
	private final EducationGradeRepository grades;
	private final EducationDataScopeService dataScopes;
	private final ExamPaperItemRepository examItems;
	private final ExamItemScoreRepository examScores;
	private final ExamCandidateRepository examCandidates;
	private final ExamSessionRepository examSessions;
	private final ExamPlanRepository examPlans;
	private final QuestionKnowledgePointRepository questionKnowledgePoints;
	private final KnowledgePointRepository knowledgePoints;
	private final StudentProfileRepository studentProfiles;
	private final SubjectRepository subjects;

	public GradeAnalysisService(
			CourseGradeRepository courseGrades,
			GradebookRepository gradebooks,
			GradebookStudentRepository students,
			CourseOfferingRepository offerings,
			AdministrativeClassRepository classes,
			EducationGradeRepository grades,
			EducationDataScopeService dataScopes,
			ExamPaperItemRepository examItems,
			ExamItemScoreRepository examScores,
			ExamCandidateRepository examCandidates,
			ExamSessionRepository examSessions,
			ExamPlanRepository examPlans,
			QuestionKnowledgePointRepository questionKnowledgePoints,
			KnowledgePointRepository knowledgePoints,
			StudentProfileRepository studentProfiles,
			SubjectRepository subjects) {
		this.courseGrades = courseGrades;
		this.gradebooks = gradebooks;
		this.students = students;
		this.offerings = offerings;
		this.classes = classes;
		this.grades = grades;
		this.dataScopes = dataScopes;
		this.examItems = examItems;
		this.examScores = examScores;
		this.examCandidates = examCandidates;
		this.examSessions = examSessions;
		this.examPlans = examPlans;
		this.questionKnowledgePoints = questionKnowledgePoints;
		this.knowledgePoints = knowledgePoints;
		this.studentProfiles = studentProfiles;
		this.subjects = subjects;
	}

	public AnalysisFilters filters(String actor) {
		List<Fact> facts = facts(actor, null, null, null, null);
		return new AnalysisFilters(
			options(facts, Fact::semesterCode, Fact::semesterCode),
			options(facts, Fact::classId, Fact::className),
			options(facts, Fact::gradeId, Fact::gradeName),
			options(facts, Fact::courseCode, Fact::courseName));
	}

	public AnalysisResponse analyze(
			String dimension,
			String semesterCode,
			String classId,
			String gradeId,
			String courseCode,
			String actor) {
		List<Fact> facts = facts(actor, semesterCode, classId, gradeId, courseCode);
		Function<Fact, String> key = switch (dimension) {
			case "CLASS" -> Fact::classId;
			case "GRADE" -> Fact::gradeId;
			case "SUBJECT" -> Fact::courseCode;
			default -> throw new IllegalArgumentException("不支持的成绩分析维度");
		};
		Function<Fact, String> name = switch (dimension) {
			case "CLASS" -> Fact::className;
			case "GRADE" -> Fact::gradeName;
			case "SUBJECT" -> Fact::courseName;
			default -> Fact::courseName;
		};
		Map<String, List<Fact>> grouped = facts.stream()
			.filter(value -> key.apply(value) != null)
			.collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.toList()));
		List<GroupMetric> metrics = grouped.entrySet().stream()
			.map(entry -> metric(entry.getKey(), name.apply(entry.getValue().getFirst()), entry.getValue()))
			.sorted(Comparator.comparing(GroupMetric::name, Comparator.nullsLast(String::compareTo)))
			.toList();
		return new AnalysisResponse(dimension, summary(facts), metrics, distribution(facts));
	}

	public List<TrendPoint> trend(
			String classId,
			String gradeId,
			String courseCode,
			String actor) {
		return facts(actor, null, classId, gradeId, courseCode).stream()
			.collect(Collectors.groupingBy(Fact::semesterCode, LinkedHashMap::new, Collectors.toList()))
			.entrySet().stream()
			.map(entry -> {
				Summary value = summary(entry.getValue());
				return new TrendPoint(entry.getKey(), value.averageScore(), value.passRate(), value.studentCount());
			})
			.sorted(Comparator.comparing(TrendPoint::semesterCode))
			.toList();
	}

	public KnowledgeAnalysisResponse knowledge(
			String semesterCode,
			String classId,
			String gradeId,
			String courseCode,
			String actor) {
		EducationDataScope scope = dataScopes.resolve(actor);
		Map<String, ExamCandidate> candidateById = examCandidates.findAll().stream()
			.collect(Collectors.toMap(ExamCandidate::getId, Function.identity()));
		Map<String, StudentProfile> studentById = studentProfiles.findAll().stream()
			.collect(Collectors.toMap(StudentProfile::getId, Function.identity()));
		Map<String, ExamSession> sessionById = examSessions.findAll().stream()
			.collect(Collectors.toMap(ExamSession::getId, Function.identity()));
		Map<String, ExamPlan> planById = examPlans.findAll().stream()
			.collect(Collectors.toMap(ExamPlan::getId, Function.identity()));
		Map<String, Subject> subjectById = subjects.findAll().stream()
			.collect(Collectors.toMap(Subject::getId, Function.identity()));
		Map<String, KnowledgePoint> pointById = knowledgePoints.findAll().stream()
			.collect(Collectors.toMap(KnowledgePoint::getId, Function.identity()));
		Map<String, KnowledgeAccumulator> values = new LinkedHashMap<>();
		for (ExamPaperItem item : examItems.findAll()) {
			ExamSession session = sessionById.get(item.getSessionId());
			ExamPlan plan = session == null ? null : planById.get(session.getPlanId());
			Subject subject = session == null ? null : subjectById.get(session.getSubjectId());
			if (item.getQuestionId() == null
					|| item.getQuestionId().isBlank()
					|| session == null
					|| !"PUBLISHED".equals(session.getScoreStatus())
					|| plan == null
					|| !blankOrEquals(semesterCode, plan.getSemesterCode())
					|| !matchesSubject(courseCode, session, subject)) {
				continue;
			}
			var mappings = questionKnowledgePoints.findByQuestionId(item.getQuestionId());
			for (var score : examScores.findByItemId(item.getId())) {
				ExamCandidate candidate = candidateById.get(score.getCandidateId());
				StudentProfile student = candidate == null
						? null
						: studentById.get(candidate.getStudentId());
				if (candidate == null
						|| student == null
						|| !dataScopes.canAccessStudent(scope, candidate.getStudentId())
						|| !blankOrEquals(classId, student.getAdministrativeClassId())
						|| !blankOrEquals(gradeId, student.getGradeId())) {
					continue;
				}
				for (var mapping : mappings) {
					KnowledgePoint point = pointById.get(mapping.getKnowledgePointId());
					if (point == null || point.isArchived() || !point.isEnabled()) {
						continue;
					}
					values.computeIfAbsent(point.getId(), ignored -> new KnowledgeAccumulator(point))
						.add(candidate.getStudentId(), score.getScore(), item.getMaxScore());
				}
			}
		}
		if (values.isEmpty()) {
			return new KnowledgeAnalysisResponse(
				false,
				"暂无已发布且已关联题库知识点的逐题成绩。历史试卷可继续查看，但不会被错误归因。",
				List.of());
		}
		List<GroupMetric> groups = values.values().stream()
			.map(value -> value.metric(pointById))
			.sorted(Comparator.comparing(GroupMetric::name))
			.toList();
		return new KnowledgeAnalysisResponse(true, null, groups);
	}

	private boolean matchesSubject(String courseCode, ExamSession session, Subject subject) {
		return courseCode == null
				|| courseCode.isBlank()
				|| Objects.equals(courseCode, session.getSubjectId())
				|| subject != null && Objects.equals(courseCode, subject.getSubjectCode());
	}

	private List<Fact> facts(
			String actor,
			String semesterCode,
			String classId,
			String gradeId,
			String courseCode) {
		EducationDataScope scope = dataScopes.resolve(actor);
		Map<String, Gradebook> published = gradebooks.findAll().stream()
			.filter(value -> "PUBLISHED".equals(value.getStatus()))
			.collect(Collectors.toMap(Gradebook::getId, Function.identity()));
		Map<String, CourseOffering> offeringById = offerings.findAllById(
			published.values().stream().map(Gradebook::getOfferingId).collect(Collectors.toSet()))
			.stream().collect(Collectors.toMap(CourseOffering::getId, Function.identity()));
		Map<String, AdministrativeClass> classById = classes.findAll().stream()
			.collect(Collectors.toMap(AdministrativeClass::getId, Function.identity()));
		Map<String, EducationGrade> gradeById = grades.findAll().stream()
			.collect(Collectors.toMap(EducationGrade::getId, Function.identity()));
		Map<String, GradebookStudent> roster = students.findAllByGradebookIdIn(published.keySet()).stream()
			.collect(Collectors.toMap(
				value -> value.getGradebookId() + ":" + value.getStudentId(),
				Function.identity(),
				(first, ignored) -> first));
		Map<String, CourseGrade> latest = new LinkedHashMap<>();
		courseGrades.findAllByGradebookIdIn(published.keySet()).forEach(value -> latest.merge(
			value.getGradebookId() + ":" + value.getStudentId(),
			value,
			(first, second) -> first.getVersionNo() >= second.getVersionNo() ? first : second));
		List<Fact> result = new ArrayList<>();
		for (Map.Entry<String, CourseGrade> entry : latest.entrySet()) {
			CourseGrade score = entry.getValue();
			Gradebook gradebook = published.get(score.getGradebookId());
			CourseOffering offering = offeringById.get(gradebook.getOfferingId());
			GradebookStudent student = roster.get(entry.getKey());
			if (offering == null || student == null) {
				continue;
			}
			AdministrativeClass administrativeClass = classById.get(student.getAdministrativeClassId());
			String currentGradeId = administrativeClass == null ? null : administrativeClass.getGradeId();
			EducationGrade currentGrade = gradeById.get(currentGradeId);
			if (!scope.fullAccess()
					&& !scope.teacherIds().contains(gradebook.getTeacherId())
					&& !scope.administrativeClassIds().contains(student.getAdministrativeClassId())
					&& !scope.gradeIds().contains(currentGradeId)
					&& !scope.campusIds().contains(offering.getCampusId())) {
				continue;
			}
			Fact fact = new Fact(
				offering.getSemesterCode(),
				offering.getCourseCode(),
				offering.getCourseName(),
				student.getAdministrativeClassId(),
				administrativeClass == null ? "未分班" : administrativeClass.getClassName(),
				currentGradeId,
				currentGrade == null ? "未分年级" : currentGrade.getGradeName(),
				score.getTotalScore(),
				Boolean.TRUE.equals(score.getPassed()));
			if (matches(fact, semesterCode, classId, gradeId, courseCode)) {
				result.add(fact);
			}
		}
		return result;
	}

	private boolean matches(Fact value, String semester, String classId, String gradeId, String courseCode) {
		return blankOrEquals(semester, value.semesterCode())
			&& blankOrEquals(classId, value.classId())
			&& blankOrEquals(gradeId, value.gradeId())
			&& blankOrEquals(courseCode, value.courseCode());
	}

	private boolean blankOrEquals(String expected, String actual) {
		return expected == null || expected.isBlank() || Objects.equals(expected, actual);
	}

	private List<FilterOption> options(
			List<Fact> facts,
			Function<Fact, String> value,
			Function<Fact, String> label) {
		return facts.stream()
			.filter(item -> value.apply(item) != null)
			.collect(Collectors.toMap(value, label, (first, ignored) -> first, LinkedHashMap::new))
			.entrySet().stream()
			.map(entry -> new FilterOption(entry.getKey(), entry.getValue()))
			.sorted(Comparator.comparing(FilterOption::label))
			.toList();
	}

	private GroupMetric metric(String key, String name, List<Fact> facts) {
		Summary summary = summary(facts);
		return new GroupMetric(
			key,
			name,
			null,
			summary.studentCount(),
			summary.averageScore(),
			summary.passRate(),
			summary.excellentRate());
	}

	private Summary summary(List<Fact> facts) {
		if (facts.isEmpty()) {
			return new Summary(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
				BigDecimal.ZERO, BigDecimal.ZERO);
		}
		BigDecimal total = facts.stream().map(Fact::score).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal highest = facts.stream().map(Fact::score).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
		BigDecimal lowest = facts.stream().map(Fact::score).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
		long passed = facts.stream().filter(Fact::passed).count();
		long excellent = facts.stream().filter(value -> value.score().compareTo(new BigDecimal("90")) >= 0).count();
		return new Summary(
			facts.size(),
			decimal(total.divide(BigDecimal.valueOf(facts.size()), 2, RoundingMode.HALF_UP)),
			decimal(highest),
			decimal(lowest),
			percent(passed, facts.size()),
			percent(excellent, facts.size()));
	}

	private List<Distribution> distribution(List<Fact> facts) {
		return List.of(
			new Distribution("90-100", count(facts, 90, null)),
			new Distribution("80-89", count(facts, 80, 90)),
			new Distribution("70-79", count(facts, 70, 80)),
			new Distribution("60-69", count(facts, 60, 70)),
			new Distribution("0-59", count(facts, null, 60)));
	}

	private long count(List<Fact> facts, Integer min, Integer max) {
		return facts.stream().filter(value -> (min == null || value.score().compareTo(BigDecimal.valueOf(min)) >= 0)
			&& (max == null || value.score().compareTo(BigDecimal.valueOf(max)) < 0)).count();
	}

	private BigDecimal percent(long numerator, long denominator) {
		return denominator == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(numerator * 100L)
			.divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
	}

	private BigDecimal decimal(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP);
	}

	private record Fact(
			String semesterCode,
			String courseCode,
			String courseName,
			String classId,
			String className,
			String gradeId,
			String gradeName,
			BigDecimal score,
			boolean passed) {
	}

	private static final class KnowledgeAccumulator {
		private final KnowledgePoint point;
		private final java.util.Set<String> studentIds = new java.util.HashSet<>();
		private BigDecimal earned = BigDecimal.ZERO;
		private BigDecimal possible = BigDecimal.ZERO;
		private long passed;
		private long excellent;
		private long attempts;

		private KnowledgeAccumulator(KnowledgePoint point) {
			this.point = point;
		}

		private void add(String studentId, BigDecimal score, BigDecimal maxScore) {
			studentIds.add(studentId);
			earned = earned.add(score);
			possible = possible.add(maxScore);
			BigDecimal rate = score.multiply(new BigDecimal("100"))
				.divide(maxScore, 4, RoundingMode.HALF_UP);
			if (rate.compareTo(new BigDecimal("60")) >= 0) {
				passed++;
			}
			if (rate.compareTo(new BigDecimal("90")) >= 0) {
				excellent++;
			}
			attempts++;
		}

		private GroupMetric metric(Map<String, KnowledgePoint> pointById) {
			BigDecimal mastery = possible.signum() == 0
				? BigDecimal.ZERO
				: earned.multiply(new BigDecimal("100"))
					.divide(possible, 2, RoundingMode.HALF_UP);
			return new GroupMetric(
				point.getId(),
				point.getName(),
				point.getParentId() == null
						? null
						: java.util.Optional.ofNullable(pointById.get(point.getParentId()))
								.map(KnowledgePoint::getName)
								.orElse("未识别上级知识点"),
				studentIds.size(),
				mastery,
				percentage(passed, attempts),
				percentage(excellent, attempts));
		}

		private BigDecimal percentage(long numerator, long denominator) {
			return denominator == 0
				? BigDecimal.ZERO
				: BigDecimal.valueOf(numerator * 100L)
					.divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
		}
	}
}

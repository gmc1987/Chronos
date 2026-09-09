package com.chronos.education.scheduling.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.education.scheduling.dao.ClassroomRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.TeacherTimeConstraintRepository;
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.model.Classroom;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.education.scheduling.model.ScheduleEntryCommand;
import com.chronos.education.scheduling.model.ScheduleEntryView;
import com.chronos.education.scheduling.model.TeacherTimeConstraint;

@Service
public class ClassSchedulingService {
	private final CourseOfferingRepository offerings;
	private final ClassroomRepository classrooms;
	private final ScheduleEntryRepository entries;
	private final TeacherTimeConstraintRepository teacherConstraints;
	private final TeachingClassMemberRepository teachingClassMembers;
	private final StudentProfileRepository students;

	public ClassSchedulingService(
			CourseOfferingRepository offerings,
			ClassroomRepository classrooms,
			ScheduleEntryRepository entries,
			TeacherTimeConstraintRepository teacherConstraints,
			TeachingClassMemberRepository teachingClassMembers,
			StudentProfileRepository students) {
		this.offerings = offerings;
		this.classrooms = classrooms;
		this.entries = entries;
		this.teacherConstraints = teacherConstraints;
		this.teachingClassMembers = teachingClassMembers;
		this.students = students;
	}

	@Transactional(readOnly = true)
	public List<CourseOffering> offerings(String semesterCode) {
		return offerings.findBySemesterCodeOrderByOfferingCode(required(semesterCode, "学期编码"));
	}

	@Transactional
	public CourseOffering saveOffering(String id, CourseOffering command) {
		validateOffering(command);
		CourseOffering value = id == null
				? new CourseOffering()
				: offerings.findById(id).orElseThrow(() -> new IllegalArgumentException("教学任务不存在"));
		value.setSemesterCode(command.getSemesterCode().trim());
		value.setOfferingCode(command.getOfferingCode().trim());
		value.setCourseCode(command.getCourseCode().trim());
		value.setCourseName(command.getCourseName().trim());
		value.setTeachingClassName(command.getTeachingClassName().trim());
		value.setTeacherId(command.getTeacherId().trim());
		value.setTeacherName(command.getTeacherName().trim());
		value.setStudentCount(command.getStudentCount());
		value.setWeeklyLessons(command.getWeeklyLessons());
		value.setCampusId(command.getCampusId());
		value.setStatus(command.getStatus() == null ? "ACTIVE" : command.getStatus());
		return offerings.save(value);
	}

	@Transactional
	public void deleteOffering(String id) {
		if (entries.countByOfferingId(id) > 0) {
			throw new IllegalStateException("教学任务已有课表安排，不能删除");
		}
		offerings.deleteById(id);
	}

	@Transactional(readOnly = true)
	public List<Classroom> classrooms() {
		return classrooms.findByEnabledTrueOrderByRoomCode();
	}

	@Transactional
	public Classroom saveClassroom(String id, Classroom command) {
		Classroom value = id == null
				? new Classroom()
				: classrooms.findById(id).orElseThrow(() -> new IllegalArgumentException("教室不存在"));
		value.setRoomCode(required(command.getRoomCode(), "教室编码"));
		value.setRoomName(required(command.getRoomName(), "教室名称"));
		value.setCampusId(command.getCampusId());
		value.setBuildingName(command.getBuildingName());
		value.setCapacity(positive(command.getCapacity(), "教室容量"));
		value.setRoomType(command.getRoomType() == null ? "STANDARD" : command.getRoomType());
		value.setEnabled(command.getEnabled() == null || command.getEnabled());
		return classrooms.save(value);
	}

	@Transactional
	public void deleteClassroom(String id) {
		if (entries.countByClassroomId(id) > 0) {
			throw new IllegalStateException("教室已有课表安排，不能删除");
		}
		classrooms.deleteById(id);
	}

	@Transactional(readOnly = true)
	public List<ScheduleEntryView> schedule(String semesterCode) {
		return schedule(semesterCode, "ALL", null);
	}

	/**
	 * 从服务端执行多维课表过滤。管理端不能依赖前端过滤，否则浏览器仍会收到
	 * 不属于所选教师、学生或班级的完整课表数据。
	 */
	@Transactional(readOnly = true)
	public List<ScheduleEntryView> schedule(
			String semesterCode,
			String dimension,
			String targetId) {
		String normalizedSemester = required(semesterCode, "学期编码");
		String normalizedDimension = dimension == null || dimension.isBlank()
				? "ALL"
				: dimension.trim().toUpperCase();
		if (!Set.of(
				"ALL",
				"TEACHER",
				"TEACHING_CLASS",
				"ADMIN_CLASS",
				"STUDENT",
				"CLASSROOM").contains(normalizedDimension)) {
			throw new IllegalArgumentException("不支持的课表查询维度：" + normalizedDimension);
		}
		if (!"ALL".equals(normalizedDimension)) {
			required(targetId, "查询对象");
		}

		List<ScheduleEntry> semesterEntries = entries
				.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc(normalizedSemester);
		Map<String, CourseOffering> offeringById = offerings
				.findBySemesterCodeOrderByOfferingCode(normalizedSemester)
				.stream()
				.collect(Collectors.toMap(CourseOffering::getId, item -> item));
		Set<String> permittedOfferingIds = resolveOfferingIds(
				normalizedDimension,
				targetId,
				offeringById);
		Predicate<ScheduleEntry> filter = switch (normalizedDimension) {
			case "ALL" -> item -> true;
			case "CLASSROOM" -> item -> targetId.equals(item.getClassroomId());
			default -> item -> permittedOfferingIds.contains(item.getOfferingId());
		};
		Map<String, Classroom> classroomById = classrooms.findAllById(
				semesterEntries.stream().map(ScheduleEntry::getClassroomId).collect(Collectors.toSet()))
				.stream()
				.collect(Collectors.toMap(Classroom::getId, item -> item));

		return semesterEntries.stream()
				.filter(filter)
				.filter(item -> !"CANCELLED".equals(item.getStatus()))
				.map(item -> view(item, offeringById, classroomById))
				.toList();
	}

	@Transactional
	public ScheduleEntryView saveEntry(String id, ScheduleEntryCommand command) {
		validateEntry(command);
		CourseOffering offering = offerings.findById(command.offeringId())
				.orElseThrow(() -> new IllegalArgumentException("教学任务不存在"));
		Classroom classroom = classrooms.findById(command.classroomId())
				.orElseThrow(() -> new IllegalArgumentException("教室不存在"));
		if (!Boolean.TRUE.equals(classroom.getEnabled())) {
			throw new IllegalStateException("教室已停用");
		}
		if (classroom.getCapacity() < offering.getStudentCount()) {
			throw new IllegalStateException("教室容量小于教学班人数");
		}
		int duration = command.durationPeriods() == null ? 1 : command.durationPeriods();
		String weekPattern = command.weekPattern() == null ? "ALL" : command.weekPattern();
		boolean teacherForbidden = teacherConstraints
				.findBySemesterCodeAndTeacherId(command.semesterCode(), offering.getTeacherId()).stream()
				.filter(item -> "FORBIDDEN".equals(item.getConstraintType()))
				.anyMatch(item -> item.getDayOfWeek().equals(command.dayOfWeek())
						&& item.getPeriodNo() >= command.periodNo()
						&& item.getPeriodNo() < command.periodNo() + duration);
		if (teacherForbidden) {
			throw new IllegalStateException("排课冲突：命中教师禁排时间");
		}
		List<ScheduleEntry> overlapping = entries.findOverlapping(
				command.semesterCode(),
				command.dayOfWeek(),
				command.periodNo(),
				command.periodNo() + duration - 1,
				command.startWeek(),
				command.endWeek(),
				id);
		for (ScheduleEntry existing : overlapping) {
			if (!weekPatternsOverlap(existing.getWeekPattern(), weekPattern)) {
				continue;
			}
			CourseOffering occupied = offerings.findById(existing.getOfferingId()).orElse(null);
			if (existing.getClassroomId().equals(command.classroomId())) {
				throw new IllegalStateException("排课冲突：该时间段教室已被占用");
			}
			if (existing.getOfferingId().equals(command.offeringId())) {
				throw new IllegalStateException("排课冲突：该教学班在相同时间已有课程");
			}
			if (occupied != null && occupied.getTeacherId().equals(offering.getTeacherId())) {
				throw new IllegalStateException("排课冲突：教师在相同时间已有课程");
			}
		}
		ScheduleEntry value = id == null
				? new ScheduleEntry()
				: entries.findById(id).orElseThrow(() -> new IllegalArgumentException("课表项不存在"));
		value.setSemesterCode(command.semesterCode().trim());
		value.setOfferingId(command.offeringId());
		value.setClassroomId(command.classroomId());
		value.setDayOfWeek(command.dayOfWeek());
		value.setPeriodNo(command.periodNo());
		value.setDurationPeriods(duration);
		value.setWeekPattern(weekPattern);
		value.setStartWeek(command.startWeek());
		value.setEndWeek(command.endWeek());
		value.setLocked(Boolean.TRUE.equals(command.locked()));
		return view(entries.save(value));
	}

	@Transactional
	public void deleteEntry(String id) {
		entries.deleteById(id);
	}

	@Transactional(readOnly = true)
	public List<TeacherTimeConstraint> teacherConstraints(String semesterCode) {
		return teacherConstraints.findBySemesterCodeOrderByTeacherIdAscDayOfWeekAscPeriodNoAsc(
				required(semesterCode, "学期编码"));
	}

	@Transactional
	public TeacherTimeConstraint saveTeacherConstraint(String id, TeacherTimeConstraint command) {
		if (command.getDayOfWeek() == null || command.getDayOfWeek() < 1 || command.getDayOfWeek() > 7) {
			throw new IllegalArgumentException("星期必须在 1 到 7 之间");
		}
		positive(command.getPeriodNo(), "节次");
		required(command.getSemesterCode(), "学期编码");
		required(command.getTeacherId(), "教师");
		if (!java.util.Set.of("FORBIDDEN", "PREFERRED").contains(command.getConstraintType())) {
			throw new IllegalArgumentException("约束类型必须为 FORBIDDEN 或 PREFERRED");
		}
		TeacherTimeConstraint value = id == null
				? command
				: teacherConstraints.findById(id)
						.map(existing -> {
							command.setId(existing.getId());
							command.setCreateBy(existing.getCreateBy());
							command.setCreateTime(existing.getCreateTime());
							return command;
						})
						.orElseThrow(() -> new IllegalArgumentException("教师时间约束不存在"));
		return teacherConstraints.save(value);
	}

	@Transactional
	public void deleteTeacherConstraint(String id) {
		teacherConstraints.deleteById(id);
	}

	private ScheduleEntryView view(ScheduleEntry entry) {
		CourseOffering offering = offerings.findById(entry.getOfferingId()).orElseThrow();
		Classroom classroom = classrooms.findById(entry.getClassroomId()).orElseThrow();
		return view(entry, Map.of(offering.getId(), offering), Map.of(classroom.getId(), classroom));
	}

	private ScheduleEntryView view(
			ScheduleEntry entry,
			Map<String, CourseOffering> offeringById,
			Map<String, Classroom> classroomById) {
		CourseOffering offering = offeringById.get(entry.getOfferingId());
		Classroom classroom = classroomById.get(entry.getClassroomId());
		if (offering == null || classroom == null) {
			throw new IllegalStateException("课表关联的教学任务或教室不存在");
		}
		return new ScheduleEntryView(
				entry.getId(),
				entry.getSemesterCode(),
				offering.getId(),
				offering.getOfferingCode(),
				offering.getCourseName(),
				offering.getTeachingClassName(),
				offering.getTeacherName(),
				classroom.getId(),
				classroom.getRoomName(),
				entry.getDayOfWeek(),
				entry.getPeriodNo(),
				entry.getDurationPeriods(),
				entry.getWeekPattern(),
				entry.getStartWeek(),
				entry.getEndWeek(),
				entry.getStatus(),
				entry.getLocked());
	}

	private Set<String> resolveOfferingIds(
			String dimension,
			String targetId,
			Map<String, CourseOffering> offeringById) {
		return switch (dimension) {
			case "ALL", "CLASSROOM" -> Set.of();
			case "TEACHER" -> offeringById.values().stream()
					.filter(item -> targetId.equals(item.getTeacherId()))
					.map(CourseOffering::getId)
					.collect(Collectors.toSet());
			case "TEACHING_CLASS" -> offeringById.containsKey(targetId)
					? Set.of(targetId)
					: Set.of();
			case "STUDENT" -> teachingClassMembers
					.findByStudentIdAndEnrollmentStatus(targetId, "ENROLLED")
					.stream()
					.map(item -> item.getOfferingId())
					.collect(Collectors.toSet());
			case "ADMIN_CLASS" -> {
				List<String> studentIds = students.findByAdministrativeClassId(targetId)
						.stream()
						.map(item -> item.getId())
						.toList();
				yield studentIds.isEmpty()
						? Set.of()
						: teachingClassMembers
								.findByStudentIdInAndEnrollmentStatus(studentIds, "ENROLLED")
								.stream()
								.map(item -> item.getOfferingId())
								.collect(Collectors.toSet());
			}
			default -> throw new IllegalArgumentException("不支持的课表维度：" + dimension);
		};
	}

	private void validateOffering(CourseOffering value) {
		required(value.getSemesterCode(), "学期编码");
		required(value.getOfferingCode(), "教学班编码");
		required(value.getCourseCode(), "课程编码");
		required(value.getCourseName(), "课程名称");
		required(value.getTeachingClassName(), "教学班名称");
		required(value.getTeacherId(), "教师");
		required(value.getTeacherName(), "教师姓名");
		positive(value.getStudentCount(), "学生人数");
		positive(value.getWeeklyLessons(), "周课时");
	}

	private void validateEntry(ScheduleEntryCommand value) {
		required(value.semesterCode(), "学期编码");
		required(value.offeringId(), "教学任务");
		required(value.classroomId(), "教室");
		if (value.dayOfWeek() == null || value.dayOfWeek() < 1 || value.dayOfWeek() > 7) {
			throw new IllegalArgumentException("星期必须在 1 到 7 之间");
		}
		positive(value.periodNo(), "节次");
		if (value.durationPeriods() != null && value.durationPeriods() <= 0) {
			throw new IllegalArgumentException("连堂节数必须大于 0");
		}
		if (value.weekPattern() != null
				&& !java.util.Set.of("ALL", "ODD", "EVEN").contains(value.weekPattern())) {
			throw new IllegalArgumentException("周模式必须为 ALL、ODD 或 EVEN");
		}
		positive(value.startWeek(), "开始周");
		positive(value.endWeek(), "结束周");
		if (value.startWeek() > value.endWeek()) {
			throw new IllegalArgumentException("开始周不能晚于结束周");
		}
	}

	private boolean weekPatternsOverlap(String left, String right) {
		return "ALL".equals(left) || "ALL".equals(right) || left.equals(right);
	}

	private int positive(Integer value, String name) {
		if (value == null || value <= 0) {
			throw new IllegalArgumentException(name + "必须大于 0");
		}
		return value;
	}

	private String required(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + "不能为空");
		}
		return value.trim();
	}
}

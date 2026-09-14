package com.chronos.education.scheduling.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.education.scheduling.dao.AdministrativeClassRepository;
import com.chronos.education.scheduling.dao.ClassroomRepository;
import com.chronos.education.scheduling.dao.CombinedOfferingSourceClassRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.model.AdministrativeClass;
import com.chronos.education.scheduling.model.CombinedOfferingSourceClass;
import com.chronos.education.scheduling.model.CombinedOfferingView;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.StudentProfile;
import com.chronos.education.scheduling.model.TeachingClassMember;

@Service
public class CombinedOfferingService {
	private final CourseOfferingRepository offerings;
	private final CombinedOfferingSourceClassRepository sources;
	private final AdministrativeClassRepository classes;
	private final StudentProfileRepository students;
	private final TeachingClassMemberRepository members;
	private final ScheduleEntryRepository schedules;
	private final ClassroomRepository classrooms;
	private final AcademicDataService academicData;

	public CombinedOfferingService(
			CourseOfferingRepository offerings,
			CombinedOfferingSourceClassRepository sources,
			AdministrativeClassRepository classes,
			StudentProfileRepository students,
			TeachingClassMemberRepository members,
			ScheduleEntryRepository schedules,
			ClassroomRepository classrooms,
			AcademicDataService academicData) {
		this.offerings = offerings;
		this.sources = sources;
		this.classes = classes;
		this.students = students;
		this.members = members;
		this.schedules = schedules;
		this.classrooms = classrooms;
		this.academicData = academicData;
	}

	@Transactional(readOnly = true)
	public CombinedOfferingView details(String offeringId) {
		CourseOffering offering = requiredOffering(offeringId);
		List<String> classIds = sourceClassIds(offeringId);
		int enrolled = enrolledCount(offeringId);
		Set<String> expectedIds = "COMBINED".equals(offering.getOfferingMode())
				? desiredStudentIds(classIds)
				: Set.of();
		Set<String> enrolledIds = new HashSet<>();
		for (TeachingClassMember member : members.findByOfferingIdOrderByCreateTime(offeringId)) {
			if ("ENROLLED".equals(member.getEnrollmentStatus())) {
				enrolledIds.add(member.getStudentId());
			}
		}
		return new CombinedOfferingView(
				offeringId,
				classIds,
				enrolled,
				expectedIds.size(),
				"COMBINED".equals(offering.getOfferingMode())
						&& !enrolledIds.equals(expectedIds));
	}

	/** 来源关系、成员与人数在同一事务内变更；任何冲突都会整体回滚。 */
	@Transactional
	public CombinedOfferingView configure(String offeringId, List<String> requestedClassIds) {
		CourseOffering offering = lockedOffering(offeringId);
		List<String> classIds = validateClasses(offering, requestedClassIds);
		if (!"COMBINED".equals(offering.getOfferingMode())
				&& enrolledCount(offeringId) > 0) {
			throw new IllegalStateException("已有手工选课学生；请先清空成员再设置合班来源");
		}
		List<CombinedOfferingSourceClass> existing = sources
				.findByOfferingIdOrderByAdministrativeClassId(offeringId);
		Set<String> requested = new HashSet<>(classIds);
		Set<String> original = new HashSet<>();
		for (CombinedOfferingSourceClass source : existing) {
			original.add(source.getAdministrativeClassId());
			if (!requested.contains(source.getAdministrativeClassId())) {
				sources.delete(source);
			}
		}
		for (String classId : classIds) {
			if (!original.contains(classId)) {
				CombinedOfferingSourceClass source = new CombinedOfferingSourceClass();
				source.setOfferingId(offeringId);
				source.setAdministrativeClassId(classId);
				sources.save(source);
			}
		}
		offering.setOfferingMode("COMBINED");
		return synchronize(offering, classIds);
	}

	@Transactional
	public CombinedOfferingView sync(String offeringId) {
		CourseOffering offering = lockedOffering(offeringId);
		if (!"COMBINED".equals(offering.getOfferingMode())) {
			throw new IllegalStateException("当前教学任务不是合班课");
		}
		List<String> classIds = validateClasses(offering, sourceClassIds(offeringId));
		return synchronize(offering, classIds);
	}

	@Transactional
	public void delete(String offeringId) {
		CourseOffering offering = lockedOffering(offeringId);
		if (!"COMBINED".equals(offering.getOfferingMode())) {
			throw new IllegalStateException("当前教学任务不是合班课");
		}
		if (schedules.countByOfferingId(offeringId) > 0) {
			throw new IllegalStateException("合班课已有课表安排，不能删除");
		}
		// 与普通教学任务删除保持相同的排课保护；先清理合班专属成员和来源关系。
		members.deleteByOfferingId(offeringId);
		sources.deleteByOfferingId(offeringId);
		offerings.delete(offering);
	}

	private CombinedOfferingView synchronize(
			CourseOffering offering,
			List<String> classIds) {
		Set<String> desired = desiredStudentIds(classIds);
		if (desired.isEmpty()) {
			throw new IllegalStateException("来源行政班没有在籍学生，无法建立合班课");
		}
		for (var entry : schedules.findByOfferingId(offering.getId())) {
			int capacity = classrooms.findById(entry.getClassroomId())
					.orElseThrow(() -> new IllegalStateException("排课教室不存在"))
					.getCapacity();
			if (capacity < desired.size()) {
				throw new IllegalStateException("合班人数超过已排教室容量，请先调整教室");
			}
		}
		for (TeachingClassMember member : members.findByOfferingIdOrderByCreateTime(offering.getId())) {
			if ("ENROLLED".equals(member.getEnrollmentStatus())
					&& !desired.contains(member.getStudentId())) {
				if (!"SOURCE_CLASS".equals(member.getEnrollmentSource())) {
					throw new IllegalStateException("合班课存在非来源班级的手工成员，请先处理");
				}
				member.setEnrollmentStatus("WITHDRAWN");
				member.setWithdrawnAt(LocalDateTime.now());
			}
		}
		// 学生人数是本次同步后的真实人数；排课的教室容量由该字段判断。
		offering.setStudentCount(desired.size());
		for (String studentId : desired) {
			academicData.enrollFromCombined(offering.getId(), studentId);
		}
		return new CombinedOfferingView(
				offering.getId(), classIds, desired.size(), desired.size(), false);
	}

	private List<String> validateClasses(CourseOffering offering, List<String> requested) {
		if (requested == null || requested.size() < 2 || requested.size() > 20) {
			throw new IllegalArgumentException("合班课须选择 2 至 20 个来源行政班");
		}
		List<String> classIds = requested.stream()
				.filter(id -> id != null && !id.isBlank())
				.map(String::trim)
				.distinct()
				.toList();
		if (classIds.size() != requested.size()) {
			throw new IllegalArgumentException("来源行政班不能重复或为空");
		}
		if (offering.getCampusId() == null || offering.getCampusId().isBlank()) {
			throw new IllegalArgumentException("合班教学任务必须先设置所属校区");
		}
		for (String classId : classIds) {
			AdministrativeClass source = classes.findById(classId)
					.orElseThrow(() -> new IllegalArgumentException("来源行政班不存在"));
			if (!"ACTIVE".equals(source.getStatus())) {
				throw new IllegalStateException("来源行政班已停用");
			}
			if (!offering.getCampusId().equals(source.getCampusId())) {
				throw new IllegalArgumentException("来源行政班必须与教学任务属于同一校区");
			}
		}
		return classIds;
	}

	private Set<String> desiredStudentIds(List<String> classIds) {
		Set<String> ids = new LinkedHashSet<>();
		for (String classId : classIds) {
			for (StudentProfile student : students.findByAdministrativeClassId(classId)) {
				if ("ACTIVE".equals(student.getEnrollmentStatus())) {
					ids.add(student.getId());
				}
			}
		}
		return ids;
	}

	private List<String> sourceClassIds(String offeringId) {
		return sources.findByOfferingIdOrderByAdministrativeClassId(offeringId).stream()
				.map(CombinedOfferingSourceClass::getAdministrativeClassId)
				.toList();
	}

	private int enrolledCount(String offeringId) {
		return (int) members.findByOfferingIdOrderByCreateTime(offeringId).stream()
				.filter(member -> "ENROLLED".equals(member.getEnrollmentStatus()))
				.count();
	}

	private CourseOffering lockedOffering(String offeringId) {
		return offerings.findForCombinedUpdate(offeringId)
				.orElseThrow(() -> new IllegalArgumentException("教学任务不存在"));
	}

	private CourseOffering requiredOffering(String offeringId) {
		return offerings.findById(offeringId)
				.orElseThrow(() -> new IllegalArgumentException("教学任务不存在"));
	}
}

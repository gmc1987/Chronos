package com.chronos.education.scheduling.service;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.AdministrativeClassRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.portal.spi.PortalContribution;
import com.chronos.portal.spi.PortalContributionProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 为统一门户提供学校概览和当前教师个人课表。 */
@Component
public class EducationPortalContributionProvider implements PortalContributionProvider {
	private final IAdminUserRepository users;
	private final TeacherAcademicProfileRepository teachers;
	private final StudentProfileRepository students;
	private final AdministrativeClassRepository classes;
	private final CourseOfferingRepository offerings;
	private final AcademicTermRepository terms;
	private final ScheduleEntryRepository entries;

	public EducationPortalContributionProvider(
			IAdminUserRepository users,
			TeacherAcademicProfileRepository teachers,
			StudentProfileRepository students,
			AdministrativeClassRepository classes,
			CourseOfferingRepository offerings,
			AcademicTermRepository terms,
			ScheduleEntryRepository entries) {
		this.users = users;
		this.teachers = teachers;
		this.students = students;
		this.classes = classes;
		this.offerings = offerings;
		this.terms = terms;
		this.entries = entries;
	}

	@Override
	public String providerCode() {
		return "DATA";
	}

	@Override
	@Transactional(readOnly = true)
	public PortalContribution load(String username) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("metrics", List.of(
				metric("教师", teachers.count()),
				metric("学生", students.count()),
				metric("班级", classes.count()),
				metric("教学任务", offerings.count())));
		data.put("allRoute", "/admin/education/scheduling");

		var account = users.findByUsername(username);
		if (account != null && account.getEmployeeId() != null) {
			teachers.findByEmployeeId(account.getEmployeeId()).ifPresent(teacher -> {
				terms.findFirstByCurrentTermTrueAndStatusOrderByStartDateDesc("ACTIVE").ifPresent(term -> {
					Map<String, CourseOffering> byId = offerings
							.findBySemesterCodeOrderByOfferingCode(term.getTermCode()).stream()
							.collect(java.util.stream.Collectors.toMap(CourseOffering::getId, value -> value));
					data.put("termName", term.getTermName());
					data.put("mySchedule", entries.findTeacherSchedule(
							teacher.getId(),
							teacher.getEmployeeId(),
							term.getTermCode()).stream()
							.map(entry -> scheduleItem(entry, byId.get(entry.getOfferingId())))
							.toList());
				});
			});
		}
		return new PortalContribution(providerCode(), true, "ok", data);
	}

	private Map<String, Object> metric(String label, long value) {
		return Map.of("label", label, "value", value);
	}

	private Map<String, Object> scheduleItem(ScheduleEntry entry, CourseOffering offering) {
		return Map.of(
				"id", entry.getId(),
				"courseName", offering == null ? "未知课程" : offering.getCourseName(),
				"teachingClassName", offering == null ? "" : offering.getTeachingClassName(),
				"dayOfWeek", entry.getDayOfWeek(),
				"periodNo", entry.getPeriodNo(),
				"durationPeriods", entry.getDurationPeriods(),
				"status", entry.getStatus());
	}
}

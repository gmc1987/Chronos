package com.chronos;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.BellPeriodRepository;
import com.chronos.education.scheduling.dao.BellScheduleRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.ScheduleGenerationJobRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.BellPeriod;
import com.chronos.education.scheduling.model.BellSchedule;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.ScheduleGenerationJob;
import com.chronos.education.scheduling.service.SchedulingAgentTimetableService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 验证教育行业部署包能装配全部平台与教务模块。
 *
 * <p>该测试会执行 Flyway 并启动 Flowable，只允许显式连接名称中包含
 * {@code test} 或 {@code verify} 的隔离 PostgreSQL 数据库，防止普通
 * {@code mvn test} 意外迁移开发或生产实例。</p>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(
        named = "CHRONOS_DB_URL",
        matches = "jdbc:postgresql:.*/[^/?]*(test|verify)[^/?]*(\\?.*)?")
class ChronosEducationApplicationTests {
	@Autowired
	private AcademicTermRepository terms;
	@Autowired
	private CourseOfferingRepository offerings;
	@Autowired
	private BellScheduleRepository schedules;
	@Autowired
	private BellPeriodRepository periods;
	@Autowired
	private SchedulingAgentTimetableService timetable;
	@Autowired
	private ScheduleGenerationJobRepository jobs;

	@Test
	void contextLoads() {
	}

	@Test
	@Transactional
	void jobLeaseOwnerAndExpiryAreEnforcedInPostgres() {
		ScheduleGenerationJob job = new ScheduleGenerationJob();
		job.setSemesterCode("AI-VERIFY");
		job.setRequestJson("{}");
		job.setRequestedBy("ai-verify");
		job.setLeaseExpiresAt(jobs.databaseTime().plusMinutes(2));
		job = jobs.saveAndFlush(job);
		String id = job.getId();
		var now = jobs.databaseTime();

		Assertions.assertEquals(1, jobs.claim(id, "worker-a", now, now.plusMinutes(2)));
		Assertions.assertEquals(0, jobs.claim(id, "worker-b", now, now.plusMinutes(2)));
		Assertions.assertEquals(0, jobs.progress(id, "worker-b", 40,
				now, now.plusMinutes(2)));
		Assertions.assertEquals(0, jobs.failExpired(id, "expired", now));
		Assertions.assertEquals(1, jobs.heartbeat(id, "worker-a", now, now.plusMinutes(3)));
		Assertions.assertEquals(0, jobs.succeed(id, "worker-b", "[]", now));
		Assertions.assertEquals(1, jobs.failExpired(id, "expired",
				now.plusMinutes(4)));
		Assertions.assertEquals(0, jobs.succeed(id, "worker-a", "[]",
				now.plusMinutes(4)));
		Assertions.assertEquals("FAILED", jobs.findById(id).orElseThrow().getStatus());
	}

	@Test
	@Transactional
	void timetableUsesPersistedTermCampusAndSchedulablePeriods() {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String termCode = "AI-VERIFY-" + suffix;
		String campus = "campus-" + suffix;
		String teacher = "teacher-" + suffix;
		AcademicTerm term = new AcademicTerm();
		term.setTermCode(termCode);
		term.setTermName(termCode);
		term.setAcademicYear("2026-2027");
		term.setTermNo(1);
		term.setStartDate(LocalDate.of(2026, 9, 1));
		term.setEndDate(LocalDate.of(2027, 1, 15));
		term.setWeekCount(18);
		term = terms.saveAndFlush(term);

		CourseOffering offering = new CourseOffering();
		offering.setSemesterCode(termCode);
		offering.setOfferingCode("AI-" + suffix);
		offering.setCourseCode("AI-COURSE");
		offering.setCourseName("AI 验证课程");
		offering.setTeachingClassName("AI 验证班");
		offering.setTeacherId(teacher);
		offering.setTeacherName("验证教师");
		offering.setStudentCount(1);
		offering.setCampusId(campus);
		offerings.saveAndFlush(offering);

		BellSchedule schedule = new BellSchedule();
		schedule.setAcademicTermId(term.getId());
		schedule.setCampusId(campus);
		schedule.setScheduleCode("AI-" + suffix);
		schedule.setScheduleName("AI 验证作息");
		schedule.setDefaultSchedule(true);
		schedule = schedules.saveAndFlush(schedule);
		savePeriod(schedule.getId(), 1, "MORNING", true);
		savePeriod(schedule.getId(), 5, "AFTERNOON", true);
		savePeriod(schedule.getId(), 6, "AFTERNOON", false);

		var segment = timetable.segment(termCode, teacher, "GLOBAL", Set.of(), "AFTERNOON");
		Assertions.assertEquals(java.util.List.of(5), segment.periodNumbers());
		Assertions.assertNull(segment.clarification());
		Assertions.assertEquals(new SchedulingAgentTimetableService.Dimensions(5, 5, 18),
				timetable.dimensions(termCode, "GLOBAL", Set.of()));
	}

	private void savePeriod(String scheduleId, int number, String segment, boolean schedulable) {
		BellPeriod period = new BellPeriod();
		period.setBellScheduleId(scheduleId);
		period.setPeriodNo(number);
		period.setPeriodName("第" + number + "节");
		period.setDaySegment(segment);
		period.setStartTime(LocalTime.of(8, 0).plusHours(number));
		period.setEndTime(LocalTime.of(8, 45).plusHours(number));
		period.setSchedulable(schedulable);
		periods.saveAndFlush(period);
	}
}

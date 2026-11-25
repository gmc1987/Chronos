package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.ErrorBookRepository;
import com.chronos.education.scheduling.dao.ErrorItemRepository;
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.ErrorBook;
import com.chronos.education.scheduling.model.ErrorItem;
import com.chronos.education.scheduling.model.TeachingClassMember;
import com.chronos.education.scheduling.model.dto.ResearchErrorDtos.ErrorManualRequest;
import com.chronos.education.scheduling.model.dto.ResearchErrorDtos.MasteryRequest;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HomeworkMistakeService {
	private static final Set<String> SOURCES = Set.of("HOMEWORK", "STUDENT_SELF");
	private static final Set<String> ENROLLED = Set.of("ACTIVE", "ENROLLED");
	private final HomeworkService homework;
	private final EducationDataScopeService scopes;
	private final TeachingClassMemberRepository members;
	private final ErrorBookRepository books;
	private final ErrorItemRepository mistakes;
	private final ResearchErrorService errorBook;

	@Transactional(readOnly = true)
	public List<ErrorItem> personal(String offeringId, Authentication actor) {
		var offering = offering(offeringId, actor);
		return items(offering, List.of(enrolledStudent(offering, actor)));
	}

	@Transactional(readOnly = true)
	public Counts counts(String offeringId, Authentication actor) {
		var offering = offering(offeringId, actor);
		if (!scopes.resolve(actor.getName()).studentIds().isEmpty()) {
			throw new AccessDeniedException("学生只能查看本人的错题");
		}
		var students = members.findByOfferingIdOrderByCreateTime(offeringId).stream()
				.filter(member -> ENROLLED.contains(member.getEnrollmentStatus()))
				.map(TeachingClassMember::getStudentId).distinct().toList();
		var entries = items(offering, students);
		long mastered = entries.stream().filter(item -> "MASTERED".equals(item.getMasteryStatus())).count();
		return new Counts(entries.size(), mastered);
	}

	@Transactional
	public ErrorItem add(String offeringId, String note, Authentication actor) {
		var offering = offering(offeringId, actor);
		String student = enrolledStudent(offering, actor);
		if (note == null || note.isBlank() || note.strip().length() > 2000) {
			throw new IllegalArgumentException("请填写 1 至 2000 字的错题记录");
		}
		return errorBook.recordManual(new ErrorManualRequest(student, offering.getCourseCode(),
				offering.getSemesterCode(), null, null, "OTHER", null, null, note.strip(),
				"STUDENT_SELF", null, null, null), actor);
	}

	@Transactional
	public ErrorItem mastery(String offeringId, String id, boolean mastered, Authentication actor) {
		var item = personal(offeringId, actor).stream().filter(value -> id.equals(value.getId())).findFirst()
				.orElseThrow(() -> new AccessDeniedException("无权修改该错题"));
		return errorBook.mastery(id, new MasteryRequest(
				mastered ? "MASTERED" : "NEEDS_PRACTICE", item.getStudentNote()), actor);
	}

	private List<ErrorItem> items(CourseOffering offering, List<String> students) {
		if (students.isEmpty()) return List.of();
		var ids = books.findByCourseIdAndSemesterIdAndStudentIdInAndArchivedFalse(
				offering.getCourseCode(), offering.getSemesterCode(), students)
				.stream().map(ErrorBook::getId).toList();
		return ids.isEmpty() ? List.of() : mistakes.findByBookIdInAndSourceTypeInOrderByCreateTimeDesc(ids, SOURCES);
	}

	private CourseOffering offering(String id, Authentication actor) {
		return homework.requireHomeworkOffering(id, actor);
	}

	private String enrolledStudent(CourseOffering offering, Authentication actor) {
		var ids = scopes.resolve(actor.getName()).studentIds();
		if (ids.size() != 1) throw new AccessDeniedException("当前账号未绑定唯一学生");
		String id = ids.iterator().next();
		if (members.findByOfferingIdAndStudentId(offering.getId(), id)
				.filter(member -> ENROLLED.contains(member.getEnrollmentStatus())).isEmpty()) {
			throw new AccessDeniedException("当前学生未加入该教学班");
		}
		return id;
	}

	public record Counts(long total, long mastered) {}
}

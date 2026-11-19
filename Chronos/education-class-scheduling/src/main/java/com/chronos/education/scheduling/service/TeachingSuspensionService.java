package com.chronos.education.scheduling.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.AdministrativeClassRepository;
import com.chronos.education.scheduling.dao.CombinedOfferingSourceClassRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.AdministrativeClass;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.ScheduleDateException;
import com.chronos.education.scheduling.model.ScheduleEntryView;
import com.chronos.education.scheduling.model.ScheduleOccurrenceView;
import com.chronos.service.iService.IAuditLogService;

import lombok.RequiredArgsConstructor;

/** 按真实日期预览并暂停具体课次，保留周期课表。 */
@Service
@RequiredArgsConstructor
public class TeachingSuspensionService {
	private final AcademicTermRepository terms;
	private final AdministrativeClassRepository classes;
	private final CombinedOfferingSourceClassRepository combinedClasses;
	private final ClassSchedulingService scheduling;
	private final ScheduleOccurrenceService calendar;
	private final SchedulePlanVersionService versions;
	private final EducationDataScopeService dataScopes;
	private final EducationResourceTransactionLock resourceLock;
	private final IAuditLogService audit;

	public record Selection(String organizationId, String semesterCode, LocalDate startDate,
			LocalDate endDate, String campusId, String majorId, String classId) {}

	public record Impact(ScheduleOccurrenceView occurrence, String conflict) {}

	public record Preview(String revision, List<Impact> impacts) {}

	public record Apply(Selection selection, String revision, String reason) {}

	@Transactional(readOnly = true)
	public Preview preview(Selection selection, String actor) {
		return preview(selection, dataScopes.resolve(actor));
	}

	@Transactional
	public int apply(Apply command, String actor) {
		Selection selection = command.selection();
		String reason = command.reason() == null ? "" : command.reason().trim();
		if (reason.isEmpty() || reason.length() > 500) {
			throw new IllegalArgumentException("请输入 1 至 500 字的停课原因");
		}
		if (selection == null) throw new IllegalArgumentException("请选择停课范围");
		EducationDataScope scope = dataScopes.resolve(actor);
		campuses(scope, selection.organizationId(), selection.semesterCode());
		resourceLock.lockSemester(selection.semesterCode());
		Preview fresh = preview(selection, scope);
		if (!fresh.revision().equals(command.revision())) {
			throw new IllegalStateException("课次已变化，请重新预览");
		}
		if (fresh.impacts().isEmpty() || fresh.impacts().stream().anyMatch(item -> item.conflict() != null)) {
			throw new IllegalStateException("预览没有可停课课次，或仍存在冲突");
		}
		for (Impact item : fresh.impacts()) {
			var occurrence = item.occurrence();
			dataScopes.assertScheduleEntryAccess(scope, occurrence.entry().id());
			ScheduleDateException suspension = new ScheduleDateException();
			suspension.setSemesterCode(selection.semesterCode());
			suspension.setSourceEntryId(occurrence.entry().id());
			suspension.setSourceDate(occurrence.date());
			suspension.setExceptionType("CANCEL");
			suspension.setMakeupRequired(true);
			suspension.setReason(reason);
			calendar.saveException(null, suspension);
		}
		audit.log(actor, "EDU_TEACHING_BATCH_SUSPEND",
				"organization=" + selection.organizationId() + ",semester=" + selection.semesterCode()
				+ ",preview=" + fresh.revision() + ",count=" + fresh.impacts().size());
		return fresh.impacts().size();
	}

	/** 目标机构和学期必须一致，防止直接使用其他学校的学期编码。 */
	@Transactional(readOnly = true)
	public Set<String> campuses(EducationDataScope scope, String organizationId, String semesterCode) {
		AcademicTerm term = term(semesterCode);
		String owner = term.getOrganizationId();
		if (owner == null || (organizationId != null && !owner.equals(organizationId))) {
			throw new AccessDeniedException("学期不属于目标机构");
		}
		return dataScopes.scheduleOrganizationCampuses(scope, owner);
	}

	@Transactional(readOnly = true)
	public Set<String> visibleOfferings(EducationDataScope scope, String organizationId, String semesterCode) {
		Set<String> campuses = campuses(scope, organizationId, semesterCode);
		return scheduling.offerings(semesterCode).stream()
				.filter(item -> item.getCampusId() != null && campuses.contains(item.getCampusId()))
				.map(CourseOffering::getId).collect(Collectors.toSet());
	}

	private Preview preview(Selection selection, EducationDataScope scope) {
		if (selection == null || selection.organizationId() == null) {
			throw new IllegalArgumentException("请选择停课机构");
		}
		AcademicTerm term = term(selection.semesterCode());
		LocalDate first = selection.startDate();
		LocalDate last = selection.endDate();
		if (first == null || last == null || last.isBefore(first)
				|| first.isBefore(term.getStartDate()) || last.isAfter(term.getEndDate())
				|| ChronoUnit.DAYS.between(first, last) >= 31) {
			throw new IllegalArgumentException("请选择学期内连续不超过 31 天的停课范围");
		}
		Map<String, Boolean> targets = targetOfferings(scope, selection);
		var published = versions.latestPublishedEntries(selection.semesterCode());
		if (published.isEmpty()) {
			throw new IllegalStateException("请先发布该学期课表，再预览停课影响");
		}
		Map<String, ScheduleEntryView> current = scheduling.schedule(selection.semesterCode()).stream()
				.collect(Collectors.toMap(ScheduleEntryView::id, entry -> entry));
		List<Impact> impacts = calendar.publishedOccurrencesBetween(
						selection.semesterCode(), first, last, published).stream()
				.filter(item -> targets.containsKey(item.entry().offeringId()))
				.map(item -> new Impact(item, conflict(item, targets.get(item.entry().offeringId()),
						current.get(item.entry().id()))))
				.sorted(Comparator.comparing(item -> item.occurrence().occurrenceKey()))
				.toList();
		return new Preview(revision(selection, impacts), impacts);
	}

	@Transactional(readOnly = true)
	public Map<String, Boolean> targetOfferings(EducationDataScope scope, Selection selection) {
		Set<String> campuses = campuses(scope, selection.organizationId(), selection.semesterCode());
		if (selection.campusId() != null && !campuses.contains(selection.campusId())) {
			throw new AccessDeniedException("无权操作所选校区");
		}
		Set<String> selectedClasses = classes.findByCampusIdIn(List.copyOf(campuses)).stream()
				.filter(item -> selection.campusId() == null || selection.campusId().equals(item.getCampusId()))
				.filter(item -> selection.majorId() == null || selection.majorId().equals(item.getMajorId()))
				.filter(item -> selection.classId() == null || selection.classId().equals(item.getId()))
				.map(AdministrativeClass::getId).collect(Collectors.toSet());
		return scheduling.offerings(selection.semesterCode()).stream()
				.filter(item -> item.getCampusId() != null && campuses.contains(item.getCampusId()))
				.filter(item -> selection.campusId() == null || selection.campusId().equals(item.getCampusId()))
				.map(item -> target(item, selectedClasses, selection))
				.filter(item -> item != null)
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	private Map.Entry<String, Boolean> target(CourseOffering offering, Set<String> selected, Selection selection) {
		if (selection.classId() == null && selection.majorId() == null) return Map.entry(offering.getId(), true);
		Set<String> sources = new HashSet<>();
		if (offering.getAdministrativeClassId() != null) sources.add(offering.getAdministrativeClassId());
		combinedClasses.findByOfferingIdOrderByAdministrativeClassId(offering.getId())
				.forEach(item -> sources.add(item.getAdministrativeClassId()));
		return sources.stream().anyMatch(selected::contains)
				? Map.entry(offering.getId(), selected.containsAll(sources)) : null;
	}

	private String conflict(ScheduleOccurrenceView item, boolean withinRange, ScheduleEntryView current) {
		if (!withinRange) return "合班还包含所选范围外的行政班";
		var published = item.entry();
		if (current == null || !Objects.equals(published.recordVersion(), current.recordVersion())) {
			return "课次已有未发布修改，请先核对并发布新课表";
		}
		return "SCHEDULED".equals(item.occurrenceStatus()) ? null : "该课次已有日期调整";
	}

	private AcademicTerm term(String code) {
		if (code == null || code.isBlank()) throw new IllegalArgumentException("请选择学期");
		return terms.findByTermCode(code).orElseThrow(() -> new IllegalArgumentException("学期不存在"));
	}

	private String revision(Selection selection, List<Impact> impacts) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest((selection.toString() + "\n" + impacts).getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("无法校验停课预览", exception);
		}
	}
}

package com.chronos.education.scheduling.service;

import com.chronos.commons.model.PageView;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.dao.TeachingCenterResourceRepository;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.TeachingCenterResource;
import com.chronos.education.scheduling.model.TeachingCenterResourceCommand;
import java.util.Set;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TeachingCenterService {
	private static final Set<String> TYPES = Set.of(
			"PLAN", "LESSON_PLAN", "PREPARATION", "COURSEWARE", "MATERIAL",
			"QUESTION_BANK", "QUESTION", "KNOWLEDGE_POINT", "MISTAKE", "RESEARCH");
	private static final Set<String> STATES = Set.of(
			"DRAFT", "SUBMITTED", "REVIEWING", "PUBLISHED", "ARCHIVED");
	private final TeachingCenterResourceRepository resources;
	private final CourseOfferingRepository offerings;
	private final ScheduleEntryRepository scheduleEntries;
	private final EducationDataScopeService scopes;

	public TeachingCenterService(
			TeachingCenterResourceRepository resources,
			CourseOfferingRepository offerings,
			ScheduleEntryRepository scheduleEntries,
			EducationDataScopeService scopes) {
		this.resources = resources;
		this.offerings = offerings;
		this.scheduleEntries = scheduleEntries;
		this.scopes = scopes;
	}

	@Transactional(readOnly = true)
	public PageView<TeachingCenterResource> page(
			String type, String offeringId, int page, int size, Authentication authentication) {
		EducationDataScope scope = scopes.resolve(authentication.getName());
		if (!TYPES.contains(type)) {
			throw new IllegalArgumentException("不支持的教学中心资源类型");
		}
		PageRequest request = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
				Sort.by(Sort.Direction.DESC, "lastUpdateTime"));
		var result = offeringId == null || offeringId.isBlank()
				? resources.findByResourceTypeAndArchivedFalse(type, request)
				: resources.findByResourceTypeAndOfferingIdAndArchivedFalse(type, offeringId, request);
		if (offeringId != null && !offeringId.isBlank()) {
			scopes.assertOfferingAccess(scope, offeringId);
		}
		return PageView.from(result);
	}

	public TeachingCenterResource create(
			TeachingCenterResourceCommand command, Authentication authentication) {
		validate(command);
		EducationDataScope scope = scopes.resolve(authentication.getName());
		if (command.offeringId() != null && !command.offeringId().isBlank()) {
			scopes.assertOfferingAccess(scope, command.offeringId());
		}
		validateSchedule(scope, command.offeringId(), command.scheduleEntryId());
		TeachingCenterResource value = new TeachingCenterResource();
		copy(value, command);
		return resources.save(value);
	}

	public TeachingCenterResource update(
			String id, TeachingCenterResourceCommand command, Authentication authentication) {
		validate(command);
		TeachingCenterResource value = resources.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("教学中心资源不存在"));
		EducationDataScope scope = scopes.resolve(authentication.getName());
		scopes.assertOfferingAccess(scope, value.getOfferingId());
		if (value.isArchived()) {
			throw new IllegalStateException("已归档资源不可修改");
		}
		if (command.offeringId() != null && !command.offeringId().isBlank()) {
			scopes.assertOfferingAccess(scope, command.offeringId());
		}
		validateSchedule(scope, command.offeringId(), command.scheduleEntryId());
		copy(value, command);
		value.setVersionNo(value.getVersionNo() + 1);
		return resources.save(value);
	}

	public TeachingCenterResource transition(
			String id, String status, Authentication authentication) {
		if (!STATES.contains(status)) {
			throw new IllegalArgumentException("不支持的资源状态");
		}
		TeachingCenterResource value = resources.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("教学中心资源不存在"));
		EducationDataScope scope = scopes.resolve(authentication.getName());
		scopes.assertOfferingAccess(scope, value.getOfferingId());
		if (!"ARCHIVED".equals(status) && !isAllowedTransition(value.getStatus(), status)) {
			throw new IllegalStateException("不允许从 " + value.getStatus() + " 流转到 " + status);
		}
		if ("PUBLISHED".equals(status) && (value.getContent() == null
				|| value.getContent().isBlank()) && (value.getFileId() == null || value.getFileId().isBlank())) {
			throw new IllegalStateException("发布前必须提供内容或文件引用");
		}
		value.setStatus(status);
		value.setArchived("ARCHIVED".equals(status));
		return resources.save(value);
	}

	private void validate(TeachingCenterResourceCommand command) {
		if (!TYPES.contains(command.resourceType())) {
			throw new IllegalArgumentException("不支持的教学中心资源类型");
		}
		if (command.fileId() != null && command.fileId().isBlank()) {
			throw new IllegalArgumentException("文件引用不能为空");
		}

		if (command.status() != null && !STATES.contains(command.status())) {
			throw new IllegalArgumentException("不支持的资源状态");
		}
	}

	private void validateSchedule(EducationDataScope scope, String offeringId, String scheduleEntryId) {
		if (scheduleEntryId == null || scheduleEntryId.isBlank()) {
			return;
		}
		var entry = scheduleEntries.findById(scheduleEntryId)
				.orElseThrow(() -> new IllegalArgumentException("课表项不存在"));
		if (!Objects.equals(offeringId, entry.getOfferingId())) {
			throw new IllegalArgumentException("课表项不属于该教学任务");
		}
		scopes.assertScheduleEntryAccess(scope, scheduleEntryId);
	}

	private void copy(TeachingCenterResource value, TeachingCenterResourceCommand command) {
		value.setResourceType(command.resourceType());
		value.setOfferingId(command.offeringId());
		value.setScheduleEntryId(command.scheduleEntryId());
		value.setTitle(command.title());
		value.setCategory(command.category());
		value.setStatus(command.status() == null ? "DRAFT" : command.status());
		value.setContent(command.content());
		value.setFileId(command.fileId());
		value.setMetadataJson(command.metadataJson());
	}

	private boolean isAllowedTransition(String current, String target) {
		return Objects.equals(current, target)
				|| "DRAFT".equals(current) && "SUBMITTED".equals(target)
				|| "SUBMITTED".equals(current) && "REVIEWING".equals(target)
				|| "REVIEWING".equals(current) && "PUBLISHED".equals(target);
	}
}

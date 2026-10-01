package com.chronos.education.scheduling.service;

import com.chronos.commons.model.PageView;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.TeachingCenterResourceRepository;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.TeachingCenterResource;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 旧教学资源表的只读兼容服务。
 *
 * <p>该表曾同时承载多个教学领域，无法表达各领域的完整状态机和版本关系。
 * 新增、修改、审核和发布必须进入对应的强类型领域服务，避免形成两份权威数据。</p>
 */
@Service
@Transactional
public class TeachingCenterService {
	private static final Set<String> TYPES = Set.of(
			"PLAN", "LESSON_PLAN", "PREPARATION", "COURSEWARE", "MATERIAL",
			"QUESTION_BANK", "QUESTION", "KNOWLEDGE_POINT", "MISTAKE", "RESEARCH");
	private final TeachingCenterResourceRepository resources;
	private final CourseOfferingRepository offerings;
	private final EducationDataScopeService scopes;

	public TeachingCenterService(
			TeachingCenterResourceRepository resources,
			CourseOfferingRepository offerings,
			EducationDataScopeService scopes) {
		this.resources = resources;
		this.offerings = offerings;
		this.scopes = scopes;
	}

	@Transactional(readOnly = true)
	public PageView<TeachingCenterResource> page(
			String type, String offeringId, int page, int size, Authentication authentication) {
		EducationDataScope scope = scopes.resolve(authentication.getName());
		if (!TYPES.contains(type)) {
			throw new IllegalArgumentException("不支持的教学中心资源类型");
		}
		if (offeringId == null || offeringId.isBlank()) {
			// 审核人可能只拥有部门/校区范围，不能把“无筛选”误判为
			// 全校权限；先按同一数据范围过滤，再做分页。
			var visible = resources.findByResourceTypeAndArchivedFalseOrderByLastUpdateTimeDesc(type).stream()
					.filter(value -> value.getOfferingId() == null
							? scope.fullAccess()
							: offerings.findById(value.getOfferingId())
									.map(offering -> scopes.canAccessOffering(scope, offering))
									.orElse(false))
					.filter(value -> !studentOnly(scope) || "PUBLISHED".equals(value.getStatus()))
					.toList();
			int from = Math.min(
					(int) PageRequest.of(
							Math.max(page, 0),
							Math.min(Math.max(size, 1), 100))
							.getOffset(),
					visible.size());
			int to = Math.min(from + Math.min(Math.max(size, 1), 100), visible.size());
			return PageView.from(new PageImpl<>(visible.subList(from, to),
					PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)), visible.size()));
		} else {
			scopes.assertOfferingAccess(scope, offeringId);
		}
		PageRequest request = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
				Sort.by(Sort.Direction.DESC, "lastUpdateTime"));
		var result = offeringId == null || offeringId.isBlank()
				? resources.findByResourceTypeAndArchivedFalse(type, request)
				: resources.findByResourceTypeAndOfferingIdAndArchivedFalse(type, offeringId, request);
		if (studentOnly(scope)) {
			var published = result.getContent().stream()
					.filter(value -> "PUBLISHED".equals(value.getStatus()))
					.toList();
			return PageView.from(new PageImpl<>(published, request, result.getTotalElements()));
		}
		return PageView.from(result);
	}

	private boolean studentOnly(EducationDataScope scope) {
		return !scope.fullAccess() && !scope.studentIds().isEmpty() && scope.teacherIds().isEmpty();
	}

	@Transactional(readOnly = true)
	public TeachingCenterResource get(String id, Authentication authentication) {
		TeachingCenterResource value = resources.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("教学中心资源不存在"));
		EducationDataScope scope = scopes.resolve(authentication.getName());
		if (value.getOfferingId() != null) {
			scopes.assertOfferingAccess(scope, value.getOfferingId());
		} else {
			scopes.assertFullAccess(scope);
		}
		return value;
	}
}

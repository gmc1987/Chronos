package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.CoursewareRepository;
import com.chronos.education.scheduling.dao.CoursewareVersionRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.PreparationMaterialRepository;
import com.chronos.education.scheduling.dao.PreparationRepository;
import com.chronos.education.scheduling.dao.LessonPlanRepository;
import com.chronos.education.scheduling.dao.LessonPlanVersionRepository;
import com.chronos.education.scheduling.dao.ResearchActivityRepository;
import com.chronos.education.scheduling.dao.ResearchGroupMemberRepository;
import com.chronos.education.scheduling.dao.ResearchGroupRepository;
import com.chronos.education.scheduling.dao.ResearchMaterialRepository;
import com.chronos.education.scheduling.dao.ResearchResultRepository;
import com.chronos.education.scheduling.dao.TeachingMaterialRepository;
import com.chronos.education.scheduling.dao.TeachingMaterialVersionRepository;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.file.service.ManagedFileAccessPolicy;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 教学中心附件读取策略。
 *
 * <p>文件表只保存业务记录编号，不复制教学数据权限。读取时沿着版本、备课
 * 或教研活动反查业务聚合，并使用当前 IAM 教育数据范围实时判定，确保教师
 * 调岗、退出教研组后不再沿用历史文件权限。</p>
 */
@Component
@Transactional(readOnly = true)
public class TeachingManagedFileAccessPolicy implements ManagedFileAccessPolicy {
	private static final String TEACHING = "EDUCATION_TEACHING";
	private static final String RESEARCH_MATERIAL = "EDUCATION_RESEARCH_MATERIAL";
	private static final String RESEARCH_RESULT = "EDUCATION_RESEARCH_RESULT";

	private final EducationDataScopeService scopes;
	private final CourseOfferingRepository offerings;
	private final PreparationMaterialRepository preparationMaterials;
	private final PreparationRepository preparations;
	private final LessonPlanVersionRepository lessonVersions;
	private final LessonPlanRepository lessons;
	private final CoursewareVersionRepository coursewareVersions;
	private final CoursewareRepository coursewares;
	private final TeachingMaterialVersionRepository materialVersions;
	private final TeachingMaterialRepository teachingMaterials;
	private final ResearchMaterialRepository researchMaterials;
	private final ResearchResultRepository researchResults;
	private final ResearchActivityRepository researchActivities;
	private final ResearchGroupRepository researchGroups;
	private final ResearchGroupMemberRepository researchGroupMembers;

	public TeachingManagedFileAccessPolicy(
			EducationDataScopeService scopes,
			CourseOfferingRepository offerings,
			PreparationMaterialRepository preparationMaterials,
			PreparationRepository preparations,
			LessonPlanVersionRepository lessonVersions,
			LessonPlanRepository lessons,
			CoursewareVersionRepository coursewareVersions,
			CoursewareRepository coursewares,
			TeachingMaterialVersionRepository materialVersions,
			TeachingMaterialRepository teachingMaterials,
			ResearchMaterialRepository researchMaterials,
			ResearchResultRepository researchResults,
			ResearchActivityRepository researchActivities,
			ResearchGroupRepository researchGroups,
			ResearchGroupMemberRepository researchGroupMembers) {
		this.scopes = scopes;
		this.offerings = offerings;
		this.preparationMaterials = preparationMaterials;
		this.preparations = preparations;
		this.lessonVersions = lessonVersions;
		this.lessons = lessons;
		this.coursewareVersions = coursewareVersions;
		this.coursewares = coursewares;
		this.materialVersions = materialVersions;
		this.teachingMaterials = teachingMaterials;
		this.researchMaterials = researchMaterials;
		this.researchResults = researchResults;
		this.researchActivities = researchActivities;
		this.researchGroups = researchGroups;
		this.researchGroupMembers = researchGroupMembers;
	}

	@Override
	public boolean canRead(String username, String businessType, String businessId) {
		if (businessId == null || businessId.isBlank()) {
			return false;
		}
		return switch (businessType) {
			case TEACHING -> canReadTeaching(username, businessId);
			case RESEARCH_MATERIAL -> researchMaterials.findById(businessId)
					.map(value -> canReadResearch(username, value.getActivityId()))
					.orElse(false);
			case RESEARCH_RESULT -> researchResults.findById(businessId)
					.map(value -> canReadResearch(username, value.getActivityId()))
					.orElse(false);
			default -> false;
		};
	}

	private boolean canReadTeaching(String username, String businessId) {
		String offeringId = preparationMaterials.findById(businessId)
				.flatMap(value -> preparations.findById(value.getPreparationId()))
				.map(value -> value.getOfferingId())
				.orElseGet(() -> coursewareVersions.findById(businessId)
						.flatMap(value -> coursewares.findById(value.getCoursewareId()))
						.map(value -> value.getOfferingId())
						.orElseGet(() -> materialVersions.findById(businessId)
								.flatMap(value -> teachingMaterials.findById(value.getMaterialId()))
								.map(value -> value.getOfferingId())
								.orElseGet(() -> lessonVersions.findById(businessId)
										.flatMap(value -> lessons.findById(value.getLessonPlanId()))
										.map(value -> value.getOfferingId())
										.orElse(null))));
		if (offeringId == null) {
			return false;
		}
		EducationDataScope scope = scopes.resolve(username);
		return offerings.findById(offeringId)
				.map(offering -> scopes.canAccessOffering(scope, offering))
				.orElse(false);
	}

	private boolean canReadResearch(String username, String activityId) {
		return researchActivities.findById(activityId)
				.flatMap(activity -> researchGroups.findById(activity.getGroupId()))
				.map(group -> {
					EducationDataScope scope = scopes.resolve(username);
					if (scope.fullAccess() || scope.teacherIds().contains(group.getLeaderTeacherId())) {
						return true;
					}
					Set<String> memberIds = researchGroupMembers.findByGroupId(group.getId()).stream()
							.map(value -> value.getTeacherId())
							.collect(java.util.stream.Collectors.toSet());
					return scope.teacherIds().stream().anyMatch(memberIds::contains);
				})
				.orElse(false);
	}
}

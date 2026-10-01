package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.PageView;
import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.TeachingCenterResource;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.education.scheduling.service.TeachingCenterService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 教学中心公共上下文与旧资源只读兼容入口。
 *
 * <p>旧资源表不再开放写接口；各教学领域使用自己的强类型控制器完成新增、审核和发布。</p>
 */
@RestController
@RequestMapping("/education/teaching-center")
public class TeachingCenterController {
	private final TeachingCenterService service;
	private final CourseOfferingRepository offerings;
	private final EducationDataScopeService scopes;

	public TeachingCenterController(
			TeachingCenterService service,
			CourseOfferingRepository offerings,
			EducationDataScopeService scopes) {
		this.service = service;
		this.offerings = offerings;
		this.scopes = scopes;
	}

	/** 门户只返回当前用户数据范围内的教学班，避免让教师手填内部 ID。 */
	@GetMapping("/offerings")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:view','education:teaching:manage')")
	public ResultData<List<CourseOffering>> visibleOfferings(Authentication authentication) {
		return ok(scopes.visibleOfferings(
				scopes.resolve(authentication.getName()),
				offerings.findAll()));
	}

	@GetMapping("/resources")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:view','education:teaching:manage')")
	public ResultData<PageView<TeachingCenterResource>> page(
			@RequestParam String type, @RequestParam(required = false) String offeringId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size, Authentication authentication) {
		return ok(service.page(type, offeringId, page, size, authentication));
	}

	private <T> ResultData<T> ok(T value) {
		return ResultData.<T>builder().code("200").msg("success").data(value).build();
	}
}

package com.chronos.controller;

import com.chronos.Idao.IConsumerUserRepository;
import com.chronos.commons.model.PageView;
import com.chronos.commons.model.ResultData;
import com.chronos.model.dto.OrganizationDTO;
import com.chronos.model.pojo.BaseEntity;
import com.chronos.model.pojo.ConsumerUser;
import com.chronos.model.pojo.Organization;
import com.chronos.model.vo.OrganizationVO;
import com.chronos.service.iService.IOrganizationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({ "/admin/organizations" })
public class OrganizationController {
	@Autowired
	private com.chronos.Idao.IOrganizationRepository organizationRepository;
	@Autowired
	private IOrganizationService organizationService;
	@Autowired
	private IConsumerUserRepository consumerUserRepository;
	@Autowired
	private com.chronos.security.DirectoryDataScopeGuard dataScope;

	@GetMapping({ "/list" })
	@PreAuthorize("@iamAuthorization.any(authentication, 'iam:organization:view','iam:organization:manage','iam:role:authorize')")
	public ResultData<PageView<OrganizationVO>> list(OrganizationDTO dto, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size, Authentication authentication) {
		PageRequest pageRequest = PageRequest.of(
				Math.max(page, 0),
				Math.min(Math.max(size, 1), 200));
		Page<Organization> orgs = this.organizationService.pageOrganizations(
				dto, (Pageable) pageRequest, dataScope.visibleOrganizationIds(authentication));

		List<String> managerIds = (List<String>) orgs.getContent().stream().map(Organization::getOrganizationManager)
				.filter(id -> (id != null && !id.isEmpty())).collect(Collectors.toList());

		Map<String, String> managerNameMap = (Map<String, String>) this.consumerUserRepository.findAllById(managerIds)
				.stream().collect(Collectors.toMap(BaseEntity::getId, ConsumerUser::getUsername));

		List<OrganizationVO> vos = orgs.getContent().stream()
				.map(org -> OrganizationVO.builder().id(org.getId()).organizationName(org.getOrganizationName())
						.orgCode(org.getOrgCode()).shortName(org.getShortName())
						.organizationType(org.getOrganizationType()).timezone(org.getTimezone())
						.status(org.getStatus()).sortOrder(org.getSortOrder())
						.parentOrganizationId(org.getParentOrgId() == null ? null : org.getParentOrgId().getId())
						.description(org.getDescription())
						.mailingAddress(org.getMailingAddress()).tel(org.getTel())
						.organizationManager(org.getOrganizationManager())
						.organizationManagerName(managerNameMap.get(org.getOrganizationManager()))
						.industries(org.getIndustries()).registerTime(org.getRegisterTime())
						.lastUpdateTime(org.getLastUpdateTime()).build())
				.collect(Collectors.toList());
		PageView<OrganizationVO> result = new PageView<>(
				vos,
				orgs.getTotalElements(),
				orgs.getTotalPages(),
				orgs.getNumber(),
				orgs.getSize(),
				orgs.isFirst(),
				orgs.isLast());
		return ResultData.<PageView<OrganizationVO>>builder()
				.code("200")
				.msg("success")
				.data(result)
				.build();
	}

	@GetMapping("/options")
	@PreAuthorize("isAuthenticated()")
	public ResultData<List<OrganizationOption>> options(Authentication authentication) {
		var allowed = dataScope.visibleOrganizationIds(authentication);
		var organizations = allowed == null ? organizationRepository.findAll() : organizationRepository.findAllById(allowed);
		var result = organizations.stream()
				.filter(org -> Integer.valueOf(1).equals(org.getStatus()))
				.sorted(java.util.Comparator.comparing(Organization::getOrganizationName))
				.map(org -> new OrganizationOption(org.getId(), org.getOrganizationName(), org.getOrganizationType()))
				.toList();
		return ResultData.<List<OrganizationOption>>builder().code("200").msg("success").data(result).build();
	}

	public record OrganizationOption(String id, String organizationName, String organizationType) {}

	@GetMapping({ "/{id}" })
	@PreAuthorize("@iamAuthorization.any(authentication, 'iam:organization:view','iam:organization:manage')")
	public ResultData<OrganizationVO> getById(@PathVariable("id") String id, Authentication authentication) {
		dataScope.requireOrganization(authentication, id);
		OrganizationVO vo = this.organizationService.getById(id);
		if (vo == null)
			return ResultData.<OrganizationVO>builder().code("404").msg("not found").data(null).build();
		return ResultData.<OrganizationVO>builder().code("200").msg("success").data(vo).build();
	}

	@GetMapping("/{id}/impact")
	@PreAuthorize("@iamAuthorization.any(authentication, 'iam:organization:view','iam:organization:manage')")
	public ResultData<Map<String, Long>> impact(@PathVariable String id, Authentication authentication) {
		dataScope.requireOrganization(authentication, id);
		return ResultData.<Map<String,Long>>builder().code("200").msg("success").data(organizationService.impact(id)).build();
	}

	@PostMapping
	@PreAuthorize("@iamAuthorization.any(authentication, 'iam:organization:create','iam:organization:manage')")
	@ResponseStatus(HttpStatus.CREATED)
	public ResultData<Void> create(@Valid @RequestBody OrganizationDTO dto, Authentication authentication) {
		dataScope.requireFullAccess(authentication);
		this.organizationService.save(dto);
		return ResultData.<Void>builder().code("201").msg("created").data(null).build();
	}

	@PutMapping
	@PreAuthorize("@iamAuthorization.any(authentication, 'iam:organization:update','iam:organization:manage')")
	public ResultData<Void> update(@Valid @RequestBody OrganizationDTO dto, Authentication authentication) {
		dataScope.requireOrganization(authentication, dto.getId());
		this.organizationService.update(dto);
		return ResultData.<Void>builder().code("200").msg("updated").data(null).build();
	}

	@DeleteMapping({ "/{id}" })
	@PreAuthorize("@iamAuthorization.any(authentication, 'iam:organization:delete','iam:organization:manage')")
	public ResultData<Void> delete(@PathVariable("id") String id, Authentication authentication) {
		dataScope.requireOrganization(authentication, id);
		this.organizationService.delete(id);
		return ResultData.<Void>builder().code("200").msg("deleted").data(null).build();
	}
}

package com.chronos.controller;

import com.chronos.Idao.IConsumerUserRepository;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
@PreAuthorize("@iamAuthorization.has(authentication, 'iam:organization:manage')")
public class OrganizationController {
	@Autowired
	private IOrganizationService organizationService;
	@Autowired
	private IConsumerUserRepository consumerUserRepository;

	@GetMapping({ "/list" })
	public ResultData<Page<OrganizationVO>> list(OrganizationDTO dto, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		PageRequest pageRequest = PageRequest.of(page, size);
		Page<Organization> orgs = this.organizationService.pageOrganizations(dto, (Pageable) pageRequest);

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
		PageImpl<OrganizationVO> pageImpl = new PageImpl<>(vos, pageRequest, orgs.getTotalElements());
		return ResultData.<Page<OrganizationVO>>builder().code("200").msg("success").data(pageImpl).build();
	}

	@GetMapping({ "/{id}" })
	public ResultData<OrganizationVO> getById(@PathVariable("id") String id) {
		OrganizationVO vo = this.organizationService.getById(id);
		if (vo == null)
			return ResultData.<OrganizationVO>builder().code("404").msg("not found").data(null).build();
		return ResultData.<OrganizationVO>builder().code("200").msg("success").data(vo).build();
	}

	@GetMapping("/{id}/impact")
	public ResultData<Map<String, Long>> impact(@PathVariable String id) {
		return ResultData.<Map<String,Long>>builder().code("200").msg("success").data(organizationService.impact(id)).build();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ResultData<Void> create(@Valid @RequestBody OrganizationDTO dto) {
		this.organizationService.save(dto);
		return ResultData.<Void>builder().code("201").msg("created").data(null).build();
	}

	@PutMapping
	public ResultData<Void> update(@Valid @RequestBody OrganizationDTO dto) {
		this.organizationService.update(dto);
		return ResultData.<Void>builder().code("200").msg("updated").data(null).build();
	}

	@DeleteMapping({ "/{id}" })
	public ResultData<Void> delete(@PathVariable("id") String id) {
		this.organizationService.delete(id);
		return ResultData.<Void>builder().code("200").msg("deleted").data(null).build();
	}
}

package com.chronos.service.impl;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.chronos.Idao.IOrganizationRepository;
import com.chronos.Idao.IOrganizationUnitRepository;
import com.chronos.Idao.IPositionRepository;
import com.chronos.Idao.IPositionRoleMappingRepository;
import com.chronos.Idao.IRoleRepository;
import com.chronos.model.pojo.PositionRoleMapping;

@Service
public class PositionRoleMappingService {
	private final IPositionRoleMappingRepository mappings;
	private final IPositionRepository positions;
	private final IRoleRepository roles;
	private final IOrganizationRepository organizations;
	private final IOrganizationUnitRepository units;

	public PositionRoleMappingService(IPositionRoleMappingRepository mappings,
			IPositionRepository positions, IRoleRepository roles,
			IOrganizationRepository organizations, IOrganizationUnitRepository units) {
		this.mappings = mappings;
		this.positions = positions;
		this.roles = roles;
		this.organizations = organizations;
		this.units = units;
	}

	public List<PositionRoleMapping> list(String positionId) {
		positions.findById(positionId).orElseThrow(() -> new IllegalArgumentException("岗位不存在"));
		return mappings.findByPositionIdOrderByPriorityDesc(positionId);
	}

	@Transactional
	public PositionRoleMapping save(PositionRoleMapping value) {
		positions.findById(value.getPositionId()).orElseThrow(() -> new IllegalArgumentException("岗位不存在"));
		roles.findById(value.getRoleId()).orElseThrow(() -> new IllegalArgumentException("角色不存在"));
		if (value.getOrganizationId() != null && !value.getOrganizationId().isBlank()) {
			organizations.findById(value.getOrganizationId())
					.orElseThrow(() -> new IllegalArgumentException("机构不存在"));
		}
		if (value.getOrganizationUnitId() != null && !value.getOrganizationUnitId().isBlank()) {
			var unit = units.findById(value.getOrganizationUnitId())
					.orElseThrow(() -> new IllegalArgumentException("部门不存在"));
			if (value.getOrganizationId() != null && !value.getOrganizationId().isBlank()
					&& !value.getOrganizationId().equals(unit.getOrgId())) {
				throw new IllegalArgumentException("部门不属于指定机构");
			}
		}
		PositionRoleMapping target = value.getId() == null ? new PositionRoleMapping()
				: mappings.findById(value.getId()).orElseThrow(() -> new IllegalArgumentException("映射不存在"));
		target.setOrganizationId(blankToNull(value.getOrganizationId()));
		target.setOrganizationUnitId(blankToNull(value.getOrganizationUnitId()));
		target.setPositionId(value.getPositionId());
		target.setRoleId(value.getRoleId());
		target.setPrimaryAssignmentOnly(Boolean.TRUE.equals(value.getPrimaryAssignmentOnly()));
		target.setDepartmentLeaderOnly(Boolean.TRUE.equals(value.getDepartmentLeaderOnly()));
		target.setPriority(value.getPriority() == null ? 0 : value.getPriority());
		target.setStatus(value.getStatus() == null ? 1 : value.getStatus());
		return mappings.save(target);
	}

	@Transactional
	public void delete(String id) {
		mappings.deleteById(id);
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}
}

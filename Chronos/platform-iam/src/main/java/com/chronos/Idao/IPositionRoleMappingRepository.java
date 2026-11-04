package com.chronos.Idao;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.model.pojo.PositionRoleMapping;

public interface IPositionRoleMappingRepository extends JpaRepository<PositionRoleMapping, String> {
	List<PositionRoleMapping> findByPositionIdAndStatusOrderByPriorityDesc(String positionId, Integer status);
	List<PositionRoleMapping> findByPositionIdOrderByPriorityDesc(String positionId);
	boolean existsByRoleId(String roleId);
}

package com.chronos.Idao;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.model.pojo.TemporaryGrant;

public interface ITemporaryGrantRepository extends JpaRepository<TemporaryGrant, String> {
	List<TemporaryGrant> findByUserIdAndPermissionCodeAndStatusAndValidFromLessThanEqualAndValidUntilGreaterThan(
			String userId, String permissionCode, String status, LocalDateTime from, LocalDateTime until);
	List<TemporaryGrant> findByStatusAndValidUntilBefore(String status, LocalDateTime now);
	List<TemporaryGrant> findByStatusAndValidFromLessThanEqual(String status, LocalDateTime now);
}

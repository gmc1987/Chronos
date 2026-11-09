package com.chronos.Idao;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.model.pojo.UserRoleAssignmentSource;

public interface IUserRoleAssignmentSourceRepository extends JpaRepository<UserRoleAssignmentSource, String> {
	List<UserRoleAssignmentSource> findByUserId(String userId);
	List<UserRoleAssignmentSource> findByUserIdAndSourceType(String userId, String sourceType);
	boolean existsByUserIdAndRoleIdAndSourceTypeAndSourceKey(
			String userId, String roleId, String sourceType, String sourceKey);
	void deleteByUserIdAndSourceType(String userId, String sourceType);
}

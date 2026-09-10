package com.chronos.Idao;

import com.chronos.model.pojo.AdminUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository("adminUserRepository")
public interface IAdminUserRepository extends JpaRepository<AdminUser, String> {
	AdminUser findByUsername(String paramString);

	Optional<AdminUser> findByEmployeeId(String employeeId);

	boolean existsByEmployeeId(String employeeId);

	long countByOrganizationId(String organizationId);

	List<AdminUser> findByRoles_Id(String roleId);

	/** 查询真正拥有指定原子权限的有效账号，供业务事故告警精确触达。 */
	@Query(value = """
			select distinct account.username
			from t_admin_user account
			join t_user_role user_role on user_role.user_id = account.id
			join t_role role on role.id = user_role.role_id
			left join t_role_permission role_permission on role_permission.role_id = role.id
			left join t_permission permission
			  on permission.id = role_permission.permission_id
			 and permission.status = 1
			where account.status = 1
			  and role.status = 1
			  and (
			    role.role_code = 'SUPER_ADMIN'
			    or permission.permission_code = :permissionCode
			  )
			order by account.username
			""", nativeQuery = true)
	List<String> findActiveUsernamesByPermissionCode(
			@Param("permissionCode") String permissionCode);
}

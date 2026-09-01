package com.chronos.Idao;

import com.chronos.model.pojo.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository("adminUserRepository")
public interface IAdminUserRepository extends JpaRepository<AdminUser, String> {
  AdminUser findByUsername(String paramString);
  Optional<AdminUser> findByEmployeeId(String employeeId);
  boolean existsByEmployeeId(String employeeId);
  long countByOrganizationId(String organizationId);
  List<AdminUser> findByRoles_Id(String roleId);
}

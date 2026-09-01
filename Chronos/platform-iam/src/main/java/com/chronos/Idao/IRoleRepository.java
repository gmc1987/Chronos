package com.chronos.Idao;

import com.chronos.model.pojo.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository("roleRepository")
public interface IRoleRepository extends JpaRepository<Role, String> {
  Role findByRoleName(String paramString);
  Role findByRoleCode(String roleCode);
}

package com.chronos.Idao;

import com.chronos.model.pojo.RoleMenuPermission;
import com.chronos.model.pojo.RoleMenuPermissionId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository("roleMenuPermissionRepository")
public interface IRoleMenuPermissionRepository extends JpaRepository<RoleMenuPermission, RoleMenuPermissionId> {
  List<RoleMenuPermission> findByRoleId(String paramString);
  
  List<RoleMenuPermission> findByRoleIdIn(Collection<String> paramCollection);
  
  void deleteByRoleId(String paramString);
}



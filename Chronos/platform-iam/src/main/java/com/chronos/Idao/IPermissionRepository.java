package com.chronos.Idao;

import com.chronos.model.pojo.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository("permissionRepository")
public interface IPermissionRepository extends JpaRepository<Permission, String> {
  Permission findByPermissionCode(String permissionCode);
}

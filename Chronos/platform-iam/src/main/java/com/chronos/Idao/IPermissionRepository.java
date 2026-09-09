package com.chronos.Idao;

import com.chronos.model.pojo.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository("permissionRepository")
public interface IPermissionRepository extends JpaRepository<Permission, String>, JpaSpecificationExecutor<Permission> {
  Permission findByPermissionCode(String permissionCode);
  Page<Permission> findByPermissionTypeIgnoreCase(String permissionType, Pageable pageable);
}

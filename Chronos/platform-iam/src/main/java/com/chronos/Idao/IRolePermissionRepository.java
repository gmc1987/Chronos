package com.chronos.Idao;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.model.pojo.RolePermission;
import com.chronos.model.pojo.RolePermissionId;
public interface IRolePermissionRepository extends JpaRepository<RolePermission,RolePermissionId> {
    List<RolePermission> findByRoleId(String roleId);
    List<RolePermission> findByRoleIdIn(List<String> roleIds);
    boolean existsByPermissionId(String permissionId);
    void deleteByRoleId(String roleId);
}

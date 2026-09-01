package com.chronos.Idao;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.model.pojo.RoleDataScope;
public interface IRoleDataScopeRepository extends JpaRepository<RoleDataScope,String> {
    List<RoleDataScope> findByRoleIdIn(List<String> roleIds);
    void deleteByRoleId(String roleId);
}

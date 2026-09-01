package com.chronos.Idao;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.model.pojo.OrganizationUnit;
public interface IOrganizationUnitRepository extends JpaRepository<OrganizationUnit,String> {
    List<OrganizationUnit> findByOrgIdOrderBySortOrderAsc(String orgId);
    List<OrganizationUnit> findByOrgIdAndStatusOrderBySortOrderAsc(String orgId, Integer status);
    boolean existsByOrgIdAndOrganizationUnitCodeAndIdNot(String orgId,String code,String id);
    boolean existsByOrgIdAndOrganizationUnitCode(String orgId,String code);
    boolean existsByParentOrganizationUnit_Id(String parentId);
    List<OrganizationUnit> findByParentOrganizationUnit_IdOrderBySortOrderAsc(String parentId);
    Optional<OrganizationUnit> findByOrgIdAndOrganizationUnitCode(String orgId,String code);
    long countByOrgId(String orgId);
    List<OrganizationUnit> findByLeaderEmployeeIdIsNotNull();
}

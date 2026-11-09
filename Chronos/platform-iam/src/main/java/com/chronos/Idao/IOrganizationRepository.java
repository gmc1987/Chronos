package com.chronos.Idao;

import com.chronos.model.pojo.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository("organizationRepository")
public interface IOrganizationRepository extends JpaRepository<Organization, String> {
  Organization findByOrgCode(String paramString);
  java.util.List<Organization> findByParentOrgId_Id(String parentId);
  Page<Organization> findByIdIn(java.util.Collection<String> ids, Pageable pageable);
  long countByParentOrgId_Id(String parentId);
}

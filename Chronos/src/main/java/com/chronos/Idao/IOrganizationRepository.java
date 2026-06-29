package com.chronos.Idao;

import com.chronos.model.pojo.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository("organizationRepository")
public interface IOrganizationRepository extends JpaRepository<Organization, String> {
  Organization findByOrgCode(String paramString);
}



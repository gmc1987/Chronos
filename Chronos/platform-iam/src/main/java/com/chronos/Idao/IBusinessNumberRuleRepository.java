package com.chronos.Idao;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.model.pojo.BusinessNumberRule;

public interface IBusinessNumberRuleRepository extends JpaRepository<BusinessNumberRule, String> {
	Optional<BusinessNumberRule> findByBusinessTypeAndOrganizationId(String businessType, String organizationId);
	List<BusinessNumberRule> findAllByOrderByBusinessTypeAscOrganizationIdAsc();
}

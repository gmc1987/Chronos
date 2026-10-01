package com.chronos.education.scheduling.dao;
import com.chronos.education.scheduling.model.DataQualityRule;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface DataQualityRuleRepository extends JpaRepository<DataQualityRule,String> {
 List<DataQualityRule> findByEnabledTrueOrderByRuleCode();
}

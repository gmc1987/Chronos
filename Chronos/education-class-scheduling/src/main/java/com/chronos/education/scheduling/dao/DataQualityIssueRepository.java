package com.chronos.education.scheduling.dao;
import com.chronos.education.scheduling.model.DataQualityIssue;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface DataQualityIssueRepository extends JpaRepository<DataQualityIssue,String> {
 List<DataQualityIssue> findByStatusOrderByDueDateAsc(String status);
 Optional<DataQualityIssue> findFirstByRuleIdAndCampusIdAndMetricCodeAndStatusIn(
   String ruleId, String campusId, String metricCode, Collection<String> statuses);
}

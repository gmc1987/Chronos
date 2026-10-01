package com.chronos.education.scheduling.dao;
import com.chronos.education.scheduling.model.DataMetricDefinition;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DataMetricDefinitionRepository extends JpaRepository<DataMetricDefinition,String> {
 Optional<DataMetricDefinition> findByMetricCode(String metricCode);
 List<DataMetricDefinition> findByEnabledTrueOrderByCategoryAscMetricCodeAsc();
}

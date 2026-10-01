package com.chronos.education.scheduling.dao;
import com.chronos.education.scheduling.model.DataDashboardWidget;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface DataDashboardWidgetRepository extends JpaRepository<DataDashboardWidget,String> {
 List<DataDashboardWidget> findByDashboardIdAndEnabledTrueOrderByPositionNo(String dashboardId);
}

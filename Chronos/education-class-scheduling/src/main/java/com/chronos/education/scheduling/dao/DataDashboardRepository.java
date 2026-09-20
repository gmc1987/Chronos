package com.chronos.education.scheduling.dao;
import com.chronos.education.scheduling.model.DataDashboard;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface DataDashboardRepository extends JpaRepository<DataDashboard,String> {
 List<DataDashboard> findByEnabledTrueOrderByCategoryAscDashboardCodeAsc();
}

package com.chronos.education.scheduling.dao;
import com.chronos.education.scheduling.model.DataDailySnapshot;
import java.time.LocalDate; import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DataDailySnapshotRepository extends JpaRepository<DataDailySnapshot,String> {
 Optional<DataDailySnapshot> findBySnapshotDateAndCampusIdAndMetricCode(LocalDate date,String campusId,String code);
 List<DataDailySnapshot> findBySnapshotDateAndCampusIdOrderByMetricCode(LocalDate date,String campusId);
 List<DataDailySnapshot> findBySnapshotDateOrderByCampusIdAscMetricCodeAsc(LocalDate date);
}

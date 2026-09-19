package com.chronos.education.scheduling.dao;
import com.chronos.education.scheduling.model.DataReportTask;
import java.time.LocalDate; import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DataReportTaskRepository extends JpaRepository<DataReportTask,String> {
 Optional<DataReportTask> findByReportTypeAndRequestedDateAndCampusId(String type,LocalDate date,String campusId);
 List<DataReportTask> findTop50ByOrderByCreateTimeDesc();
}

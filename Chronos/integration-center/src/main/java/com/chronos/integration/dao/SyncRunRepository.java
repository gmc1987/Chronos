package com.chronos.integration.dao;
import com.chronos.integration.model.SyncRun; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface SyncRunRepository extends JpaRepository<SyncRun,String> { List<SyncRun> findByJobIdOrderByStartedAtDesc(String jobId); }

package com.chronos.integration.dao;
import com.chronos.integration.model.SyncJob; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface SyncJobRepository extends JpaRepository<SyncJob,String> { List<SyncJob> findByStatus(String status); }

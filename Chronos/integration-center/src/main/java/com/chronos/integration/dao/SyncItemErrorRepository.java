package com.chronos.integration.dao;
import com.chronos.integration.model.SyncItemError; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface SyncItemErrorRepository extends JpaRepository<SyncItemError,String> { List<SyncItemError> findByRunId(String runId); }

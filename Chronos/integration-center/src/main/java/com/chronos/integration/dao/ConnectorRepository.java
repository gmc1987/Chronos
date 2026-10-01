package com.chronos.integration.dao;
import com.chronos.integration.model.Connector; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface ConnectorRepository extends JpaRepository<Connector,String> { List<Connector> findByStatus(String status); }

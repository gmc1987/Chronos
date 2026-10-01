package com.chronos.integration.dao;
import com.chronos.integration.model.DeadLetter; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface DeadLetterRepository extends JpaRepository<DeadLetter,String> { List<DeadLetter> findByStatus(String status); }

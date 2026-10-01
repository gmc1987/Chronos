package com.chronos.education.grade.dao;

import com.chronos.education.grade.model.GradeRuleSet;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface GradeRuleSetRepository extends JpaRepository<GradeRuleSet, String> {
    Optional<GradeRuleSet> findFirstByIdAndStatus(String id, String status);
    Optional<GradeRuleSet> findFirstByCodeAndStatusOrderByVersionNoDesc(String code, String status);
}

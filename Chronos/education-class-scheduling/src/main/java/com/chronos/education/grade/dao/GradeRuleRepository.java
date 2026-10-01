package com.chronos.education.grade.dao;

import com.chronos.education.grade.model.GradeRule;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface GradeRuleRepository extends JpaRepository<GradeRule, String> {
    List<GradeRule> findByRuleSetIdOrderBySortOrder(String ruleSetId);
}

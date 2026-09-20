package com.chronos.education.grade.service;

import com.chronos.education.grade.dao.*;
import com.chronos.education.grade.model.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GradeRuleEvaluationServiceTest {
    @Test
    void evaluatesPublishedConfiguredRuleAndCarriesVersionSnapshot() {
        GradeRuleSetRepository sets = mock(GradeRuleSetRepository.class);
        GradeRuleRepository rules = mock(GradeRuleRepository.class);
        GradeRuleSet set = new GradeRuleSet();
        set.setId("rules-1");
        set.setVersionNo(3);
        GradeRule rule = new GradeRule();
        rule.setId("rule-1");
        rule.setRuleSetId("rules-1");
        rule.setMinScore(BigDecimal.valueOf(80));
        rule.setMaxScore(BigDecimal.valueOf(100));
        rule.setGradeLevel("A");
        rule.setGradePoint(BigDecimal.valueOf(4));
        rule.setPassed(true);
        when(sets.findFirstByIdAndStatus("rules-1", "PUBLISHED")).thenReturn(Optional.of(set));
        when(rules.findByRuleSetIdOrderBySortOrder("rules-1")).thenReturn(List.of(rule));

        GradeRuleEvaluationService.Evaluation result =
                new GradeRuleEvaluationService(sets, rules).evaluate(BigDecimal.valueOf(88), "rules-1");

        assertEquals("rules-1", result.ruleSetId());
        assertEquals(3, result.ruleSetVersion());
        assertEquals("A", result.gradeLevel());
        assertEquals(BigDecimal.valueOf(4), result.gradePoint());
        assertTrue(result.passed());
        assertTrue(result.ruleSnapshotJson().contains("\"grade\":\"A\""));
    }

    @Test
    void platformDefaultIsExplicitAndFailingScoresAreNotPassed() {
        GradeRuleEvaluationService.Evaluation result =
                GradeRuleEvaluationService.platformDefault(BigDecimal.valueOf(59));

        assertEquals("PLATFORM_DEFAULT", result.ruleSetId());
        assertEquals("F", result.gradeLevel());
        assertEquals(BigDecimal.ZERO, result.gradePoint());
        assertFalse(result.passed());
    }
}

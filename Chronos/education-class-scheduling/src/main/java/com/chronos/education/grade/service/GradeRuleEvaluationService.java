package com.chronos.education.grade.service;

import com.chronos.education.grade.dao.*;
import com.chronos.education.grade.model.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;

@Service
public class GradeRuleEvaluationService {
    public record Evaluation(String ruleSetId, int ruleSetVersion, String ruleSnapshotJson,
                             String gradeLevel, BigDecimal gradePoint, boolean passed) {}

    private final GradeRuleSetRepository ruleSets;
    private final GradeRuleRepository rules;

    public GradeRuleEvaluationService(GradeRuleSetRepository ruleSets, GradeRuleRepository rules) {
        this.ruleSets = ruleSets;
        this.rules = rules;
    }

    public Evaluation evaluate(BigDecimal score, String ruleSetId) {
        GradeRuleSet set = ruleSetId == null
                ? ruleSets.findFirstByCodeAndStatusOrderByVersionNoDesc("PLATFORM_DEFAULT", "PUBLISHED").orElse(null)
                : ruleSets.findFirstByIdAndStatus(ruleSetId, "PUBLISHED").orElse(null);
        List<GradeRule> configured = set == null ? List.of() : rules.findByRuleSetIdOrderBySortOrder(set.getId());
        if (configured.isEmpty()) {
            return platformDefault(score);
        }

        GradeRule matched = configured.stream()
                .filter(x -> score.compareTo(x.getMinScore()) >= 0 && score.compareTo(x.getMaxScore()) <= 0)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("成绩不在规则集覆盖范围内"));
        return new Evaluation(set.getId(), set.getVersionNo(), snapshot(set, configured),
                matched.getGradeLevel(), matched.getGradePoint(), matched.getPassed());
    }

    public Evaluation describe(String ruleSetId) {
        GradeRuleSet set = ruleSetId == null
                ? ruleSets.findFirstByCodeAndStatusOrderByVersionNoDesc("PLATFORM_DEFAULT", "PUBLISHED").orElse(null)
                : ruleSets.findFirstByIdAndStatus(ruleSetId, "PUBLISHED").orElse(null);
        List<GradeRule> configured = set == null ? List.of() : rules.findByRuleSetIdOrderBySortOrder(set.getId());
        if (configured.isEmpty()) return platformDefault(BigDecimal.ZERO);
        GradeRule first = configured.get(0);
        return new Evaluation(set.getId(), set.getVersionNo(), snapshot(set, configured),
                first.getGradeLevel(), first.getGradePoint(), first.getPassed());
    }

    public static Evaluation platformDefault(BigDecimal score) {
        String level;
        BigDecimal point;
        boolean passed;
        if (score.compareTo(BigDecimal.valueOf(90)) >= 0) { level = "A"; point = BigDecimal.valueOf(4); passed = true; }
        else if (score.compareTo(BigDecimal.valueOf(80)) >= 0) { level = "B"; point = BigDecimal.valueOf(3); passed = true; }
        else if (score.compareTo(BigDecimal.valueOf(70)) >= 0) { level = "C"; point = BigDecimal.valueOf(2); passed = true; }
        else if (score.compareTo(BigDecimal.valueOf(60)) >= 0) { level = "D"; point = BigDecimal.valueOf(1); passed = true; }
        else { level = "F"; point = BigDecimal.ZERO; passed = false; }
        return new Evaluation("PLATFORM_DEFAULT", 1,
                "[{\"min\":0,\"max\":59.99,\"grade\":\"F\",\"point\":0,\"passed\":false},"
                        + "{\"min\":60,\"max\":69.99,\"grade\":\"D\",\"point\":1,\"passed\":true},"
                        + "{\"min\":70,\"max\":79.99,\"grade\":\"C\",\"point\":2,\"passed\":true},"
                        + "{\"min\":80,\"max\":89.99,\"grade\":\"B\",\"point\":3,\"passed\":true},"
                        + "{\"min\":90,\"max\":100,\"grade\":\"A\",\"point\":4,\"passed\":true}]",
                level, point, passed);
    }

    private String snapshot(GradeRuleSet set, List<GradeRule> configured) {
        return configured.stream().map(x -> "{\"min\":" + x.getMinScore() + ",\"max\":" + x.getMaxScore()
                + ",\"grade\":\"" + x.getGradeLevel() + "\",\"point\":" + x.getGradePoint()
                + ",\"passed\":" + x.getPassed() + "}").reduce("[", (a, b) -> a.equals("[") ? a + b : a + "," + b) + "]";
    }
}

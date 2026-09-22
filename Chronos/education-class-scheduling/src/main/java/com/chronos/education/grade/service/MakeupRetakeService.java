package com.chronos.education.grade.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.education.grade.dao.CourseGradeRepository;
import com.chronos.education.grade.dao.GradebookRepository;
import com.chronos.education.grade.dao.MakeupRetakeRecordRepository;
import com.chronos.education.grade.dao.GradeRuleSetRepository;
import com.chronos.education.grade.dto.MakeupRetakeDtos.CreateCommand;
import com.chronos.education.grade.model.CourseGrade;
import com.chronos.education.grade.model.Gradebook;
import com.chronos.education.grade.model.MakeupRetakeRecord;
import com.chronos.education.scheduling.dao.ExamCandidateRepository;
import com.chronos.education.scheduling.dao.ExamItemScoreRepository;
import com.chronos.education.scheduling.dao.ExamPaperItemRepository;
import com.chronos.education.scheduling.dao.ExamRoomRepository;
import com.chronos.education.scheduling.dao.ExamSessionRepository;
import com.chronos.education.scheduling.model.ExamCandidate;
import com.chronos.education.scheduling.model.ExamItemScore;
import com.chronos.education.scheduling.model.ExamPaperItem;
import com.chronos.education.scheduling.model.ExamRoom;
import com.chronos.education.scheduling.model.ExamSession;
import com.chronos.service.iService.IAuditLogService;

@Service
public class MakeupRetakeService {
    private final MakeupRetakeRecordRepository records;
    private final CourseGradeRepository courseGrades;
    private final GradebookRepository gradebooks;
    private final ExamSessionRepository sessions;
    private final ExamRoomRepository rooms;
    private final ExamCandidateRepository candidates;
    private final ExamPaperItemRepository paperItems;
    private final ExamItemScoreRepository scores;
    private final IAuditLogService audit;
    private final GradeRuleSetRepository ruleSets;

    public MakeupRetakeService(MakeupRetakeRecordRepository records, CourseGradeRepository courseGrades,
            GradebookRepository gradebooks, ExamSessionRepository sessions, ExamRoomRepository rooms,
            ExamCandidateRepository candidates, ExamPaperItemRepository paperItems,
            ExamItemScoreRepository scores, IAuditLogService audit, GradeRuleSetRepository ruleSets) {
        this.records = records;
        this.courseGrades = courseGrades;
        this.gradebooks = gradebooks;
        this.sessions = sessions;
        this.rooms = rooms;
        this.candidates = candidates;
        this.paperItems = paperItems;
        this.scores = scores;
        this.audit = audit;
        this.ruleSets = ruleSets;
    }

    @Transactional
    public MakeupRetakeRecord create(CreateCommand command, String actor) {
        if (!"MAKEUP".equals(command.recordType()) && !"RETAKE".equals(command.recordType())) {
            throw new IllegalArgumentException("补考/重修类型无效");
        }
        CourseGrade source = courseGrades.findById(command.sourceCourseGradeId())
                .orElseThrow(() -> new IllegalArgumentException("原课程成绩不存在"));
        Gradebook book = gradebooks.findById(source.getGradebookId())
                .filter(x -> "PUBLISHED".equals(x.getStatus()))
                .orElseThrow(() -> new IllegalStateException("只能基于已发布课程成绩办理"));
        ExamSession session = sessions.findById(command.examSessionId())
                .filter(x -> "PUBLISHED".equals(x.getStatus()) && "PUBLISHED".equals(x.getScoreStatus())
                        && x.getScoresPublishedAt() != null)
                .orElseThrow(() -> new IllegalStateException("考试成绩尚未确认并发布"));
        ExamCandidate candidate = candidateFor(session, source.getStudentId());
        MakeupRetakeRecord existing = records.findByExamSessionIdAndExamCandidateIdAndRecordType(
                session.getId(), candidate.getId(), command.recordType()).orElse(null);
        if (existing != null) {
            return existing;
        }
        ExamResult result = examResult(session.getId(), candidate.getId());
        MakeupRetakeRecord record = new MakeupRetakeRecord();
        record.setSourceCourseGradeId(source.getId());
        record.setSourceGradebookId(source.getGradebookId());
        record.setStudentId(source.getStudentId());
        record.setOfferingId(book.getOfferingId());
        record.setExamSessionId(session.getId());
        record.setExamCandidateId(candidate.getId());
        record.setRecordType(command.recordType());
        record.setResultScore(result.score());
        record.setMaxScore(result.maxScore());
        record.setOriginalScore(source.getTotalScore());
        record.setStrategy(ruleSets.findFirstByIdAndStatus(source.getGradeRuleSetId(), "PUBLISHED")
                .map(x -> x.getMakeupStrategy()).orElse("SEPARATE_RECORD"));
        record.setSourceSnapshotHash(hash(session.getId() + "|" + candidate.getId() + "|" + result.score()));
        audit.log(actor, "EDU_MAKEUP_RETAKE_CREATE", "recordId=" + record.getId());
        return records.save(record);
    }

    @Transactional
    public MakeupRetakeRecord submit(String id, String actor) {
        MakeupRetakeRecord record = record(id);
        if (!"DRAFT".equals(record.getStatus())) throw new IllegalStateException("当前状态不可提交");
        record.setStatus("SUBMITTED");
        record.setSubmittedBy(actor);
        record.setSubmittedAt(LocalDateTime.now());
        audit.log(actor, "EDU_MAKEUP_RETAKE_SUBMIT", "recordId=" + id);
        return records.save(record);
    }

    @Transactional
    public MakeupRetakeRecord decide(String id, boolean approved, String actor) {
        MakeupRetakeRecord record = record(id);
        if (!"SUBMITTED".equals(record.getStatus())) throw new IllegalStateException("当前状态不可审批");
        record.setStatus(approved ? "APPROVED" : "REJECTED");
        record.setApprovedBy(actor);
        record.setApprovedAt(LocalDateTime.now());
        audit.log(actor, approved ? "EDU_MAKEUP_RETAKE_APPROVE" : "EDU_MAKEUP_RETAKE_REJECT",
                "recordId=" + id);
        return records.save(record);
    }

    @Transactional
    public MakeupRetakeRecord publish(String id, String actor) {
        MakeupRetakeRecord record = record(id);
        if ("PUBLISHED".equals(record.getStatus())) return record;
        if (!"APPROVED".equals(record.getStatus())) throw new IllegalStateException("仅审批通过后可发布");
        record.setEffectiveScore(effectiveScore(record));
        record.setPublishedSnapshotJson("{\"recordId\":\"" + record.getId() + "\",\"sourceGradeId\":\""
                + record.getSourceCourseGradeId() + "\",\"studentId\":\"" + record.getStudentId()
                + "\",\"offeringId\":\"" + record.getOfferingId() + "\",\"recordType\":\""
                + record.getRecordType() + "\",\"originalScore\":" + record.getOriginalScore()
                + ",\"resultScore\":" + record.getResultScore() + ",\"effectiveScore\":"
                + record.getEffectiveScore() + ",\"strategy\":\"" + record.getStrategy() + "\"}");
        record.setStatus("PUBLISHED");
        record.setPublishedBy(actor);
        record.setPublishedAt(LocalDateTime.now());
        audit.log(actor, "EDU_MAKEUP_RETAKE_PUBLISH", "recordId=" + id);
        return records.save(record);
    }

    @Transactional(readOnly = true)
    public List<MakeupRetakeRecord> visibleToStudent(String studentId) {
        return records.findByStudentIdAndStatusOrderByPublishedAtDesc(studentId, "PUBLISHED");
    }

    @Transactional(readOnly = true)
    public List<MakeupRetakeRecord> visibleToParent(String parentId,
            com.chronos.education.scheduling.dao.StudentGuardianRepository guardians) {
        return guardians.findByParentIdOrderByCreateTime(parentId).stream()
                .flatMap(x -> visibleToStudent(x.getStudentId()).stream()).toList();
    }

    private MakeupRetakeRecord record(String id) {
        return records.findById(id).orElseThrow(() -> new IllegalArgumentException("补考/重修记录不存在"));
    }

    private ExamCandidate candidateFor(ExamSession session, String studentId) {
        return rooms.findBySessionId(session.getId()).stream()
                .flatMap(room -> candidates.findByRoomIdOrderBySeatNoAsc(room.getId()).stream())
                .filter(candidate -> studentId.equals(candidate.getStudentId()))
                .findFirst().orElseThrow(() -> new IllegalStateException("考试中没有该学生的真实考生记录"));
    }

    private ExamResult examResult(String sessionId, String candidateId) {
        List<ExamPaperItem> items = paperItems.findBySessionIdOrderByQuestionNoAsc(sessionId);
        if (items.isEmpty()) throw new IllegalStateException("考试没有已发布试卷题目");
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal max = BigDecimal.ZERO;
        for (ExamPaperItem item : items) {
            ExamItemScore score = scores.findByItemIdAndCandidateId(item.getId(), candidateId)
                    .orElseThrow(() -> new IllegalStateException("考试逐题成绩不完整，禁止生成结果"));
            total = total.add(score.getScore());
            max = max.add(item.getMaxScore());
        }
        return new ExamResult(total, max);
    }

    private BigDecimal effectiveScore(MakeupRetakeRecord record) {
        return switch (record.getStrategy()) {
            case "HIGHEST" -> record.getOriginalScore().max(record.getResultScore());
            case "PASS_CAP" -> record.getResultScore().compareTo(record.getOriginalScore()) > 0
                    ? record.getResultScore() : record.getOriginalScore();
            case "SEPARATE_RECORD", "OVERWRITE" -> record.getResultScore();
            default -> throw new IllegalStateException("未知补考/重修策略");
        };
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("来源快照哈希失败", e);
        }
    }

    private record ExamResult(BigDecimal score, BigDecimal maxScore) {}
}

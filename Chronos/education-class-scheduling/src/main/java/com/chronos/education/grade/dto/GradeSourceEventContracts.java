package com.chronos.education.grade.dto;
import java.math.BigDecimal; import java.time.OffsetDateTime;
/** 预留跨中心事件契约；第一片不消费、不伪造这些事件。 */
public final class GradeSourceEventContracts { private GradeSourceEventContracts() {} public record ExamScoresConfirmedV1(String eventId,String eventType,OffsetDateTime occurredAt,int payloadVersion,String examPlanId,String sessionId,String offeringId,String studentId,BigDecimal rawScore,BigDecimal maxScore,String specialStatus,OffsetDateTime confirmedAt) {} public record HomeworkGradesPublishedV1(String eventId,String eventType,OffsetDateTime occurredAt,int payloadVersion,String assignmentId,String offeringId,String studentId,BigDecimal score,BigDecimal maxScore,OffsetDateTime publishedAt) {} }

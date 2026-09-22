package com.chronos.education.grade.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.chronos.education.grade.dao.*;
import com.chronos.education.grade.model.MakeupRetakeRecord;
import com.chronos.service.iService.IAuditLogService;

class MakeupRetakeServiceTest {
    @Test
    void publishIsIdempotentAndNeverChangesThePublishedSourceGrade() {
        MakeupRetakeRecordRepository records = mock(MakeupRetakeRecordRepository.class);
        MakeupRetakeRecord record = new MakeupRetakeRecord();
        record.setId("record-1");
        record.setSourceCourseGradeId("source-1");
        record.setStudentId("student-1");
        record.setOfferingId("offering-1");
        record.setOriginalScore(new BigDecimal("42"));
        record.setResultScore(new BigDecimal("76"));
        record.setStrategy("HIGHEST");
        record.setStatus("APPROVED");
        when(records.findById("record-1")).thenReturn(Optional.of(record));
        when(records.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MakeupRetakeService service = new MakeupRetakeService(records, mock(CourseGradeRepository.class),
                mock(GradebookRepository.class), mock(com.chronos.education.scheduling.dao.ExamSessionRepository.class),
                mock(com.chronos.education.scheduling.dao.ExamRoomRepository.class),
                mock(com.chronos.education.scheduling.dao.ExamCandidateRepository.class),
                mock(com.chronos.education.scheduling.dao.ExamPaperItemRepository.class),
                mock(com.chronos.education.scheduling.dao.ExamItemScoreRepository.class),
                mock(IAuditLogService.class), mock(GradeRuleSetRepository.class));

        service.publish("record-1", "publisher");
        assertEquals(new BigDecimal("76"), record.getEffectiveScore());
        assertEquals("source-1", record.getSourceCourseGradeId());
        assertEquals("PUBLISHED", record.getStatus());

        service.publish("record-1", "publisher");
        verify(records, times(2)).findById("record-1");
    }
}

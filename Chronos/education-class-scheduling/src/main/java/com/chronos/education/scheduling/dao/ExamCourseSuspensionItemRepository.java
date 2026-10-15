package com.chronos.education.scheduling.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

import com.chronos.education.scheduling.model.ExamCourseSuspensionItem;

public interface ExamCourseSuspensionItemRepository
		extends JpaRepository<ExamCourseSuspensionItem, String> {
	List<ExamCourseSuspensionItem> findByRequestId(String requestId);

	@Query("""
			select count(item) > 0 from ExamCourseSuspensionItem item,
			ExamCourseSuspensionRequest request, ScheduleDateException exception
			where item.requestId = request.id and item.exceptionId = exception.id
			and item.sourceEntryId = :entryId and item.sourceDate = :sourceDate
			and request.planId = :planId and request.status = 'APPROVED'
			and exception.status = 'ACTIVE' and exception.exceptionType = 'CANCEL'
			""")
	boolean existsActiveApprovedSuspension(@Param("entryId") String entryId,
			@Param("sourceDate") LocalDate sourceDate, @Param("planId") String planId);
}

package com.chronos.education.scheduling.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.ExamCourseSuspensionItem;

public interface ExamCourseSuspensionItemRepository
		extends JpaRepository<ExamCourseSuspensionItem, String> {
	List<ExamCourseSuspensionItem> findByRequestId(String requestId);
}

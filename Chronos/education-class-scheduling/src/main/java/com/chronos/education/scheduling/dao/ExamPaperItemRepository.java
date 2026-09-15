package com.chronos.education.scheduling.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.ExamPaperItem;

public interface ExamPaperItemRepository extends JpaRepository<ExamPaperItem, String> {
	List<ExamPaperItem> findBySessionIdOrderByQuestionNoAsc(String sessionId);
}

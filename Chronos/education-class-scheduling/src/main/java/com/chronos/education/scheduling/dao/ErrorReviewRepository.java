package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.ErrorReview;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErrorReviewRepository extends JpaRepository<ErrorReview, String> {
	List<ErrorReview> findByErrorItemIdOrderByReviewedAtDesc(String errorItemId);
}

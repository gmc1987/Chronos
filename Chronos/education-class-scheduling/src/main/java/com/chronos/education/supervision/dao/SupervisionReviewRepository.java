package com.chronos.education.supervision.dao;

import com.chronos.education.supervision.model.SupervisionReview;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupervisionReviewRepository extends JpaRepository<SupervisionReview, String> {
}

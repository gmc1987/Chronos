package com.chronos.Idao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.model.pojo.AccessReviewItem;

public interface IAccessReviewItemRepository extends JpaRepository<AccessReviewItem, String> {
	List<AccessReviewItem> findByReviewIdOrderByCreateTimeAsc(String reviewId);
}

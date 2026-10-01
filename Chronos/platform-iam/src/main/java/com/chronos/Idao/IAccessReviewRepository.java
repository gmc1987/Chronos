package com.chronos.Idao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.model.pojo.AccessReview;

public interface IAccessReviewRepository extends JpaRepository<AccessReview, String> {
	List<AccessReview> findAllByOrderByCreateTimeDesc();
}

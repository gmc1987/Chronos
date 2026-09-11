package com.chronos.ai.dao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chronos.ai.model.AiModel;

public interface AiModelRepository extends JpaRepository<AiModel, String> {
	@Query("""
			select model from AiModel model
			where (:modelName = '' or lower(model.modelName) like lower(concat('%', :modelName, '%')))
			  and (:provider = '' or model.provider = :provider)
			  and (:status is null or model.status = :status)
			order by model.createTime desc
			""")
	Page<AiModel> search(
			@Param("modelName") String modelName,
			@Param("provider") String provider,
			@Param("status") Integer status,
			Pageable pageable);
}

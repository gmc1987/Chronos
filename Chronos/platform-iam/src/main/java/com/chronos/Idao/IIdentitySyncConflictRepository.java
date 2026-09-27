package com.chronos.Idao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.model.pojo.IdentitySyncConflict;

public interface IIdentitySyncConflictRepository extends JpaRepository<IdentitySyncConflict, String> {
	List<IdentitySyncConflict> findByStatusOrderByCreateTimeDesc(String status);
}

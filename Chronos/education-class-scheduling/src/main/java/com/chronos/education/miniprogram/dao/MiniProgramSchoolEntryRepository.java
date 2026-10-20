package com.chronos.education.miniprogram.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.miniprogram.model.MiniProgramSchoolEntry;

public interface MiniProgramSchoolEntryRepository extends JpaRepository<MiniProgramSchoolEntry, String> {
	Optional<MiniProgramSchoolEntry> findByOrganizationId(String organizationId);
	Optional<MiniProgramSchoolEntry> findBySceneCodeAndStatus(String sceneCode, String status);
}

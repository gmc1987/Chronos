package com.chronos.education.miniprogram.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chronos.education.miniprogram.model.MiniProgramBindingSession;

import jakarta.persistence.LockModeType;

public interface MiniProgramBindingSessionRepository extends JpaRepository<MiniProgramBindingSession, String> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select session from MiniProgramBindingSession session where session.tokenHash = :tokenHash")
	Optional<MiniProgramBindingSession> findLockedByTokenHash(@Param("tokenHash") String tokenHash);
}

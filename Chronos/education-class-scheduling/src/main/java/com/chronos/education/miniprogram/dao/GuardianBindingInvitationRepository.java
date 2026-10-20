package com.chronos.education.miniprogram.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chronos.education.miniprogram.model.GuardianBindingInvitation;

import jakarta.persistence.LockModeType;

public interface GuardianBindingInvitationRepository extends JpaRepository<GuardianBindingInvitation, String> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select invitation from GuardianBindingInvitation invitation where invitation.sceneCode = :sceneCode")
	Optional<GuardianBindingInvitation> findLockedBySceneCode(@Param("sceneCode") String sceneCode);
	Optional<GuardianBindingInvitation> findBySceneCode(String sceneCode);
}

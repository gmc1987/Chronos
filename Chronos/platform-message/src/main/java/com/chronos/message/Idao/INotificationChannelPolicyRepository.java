package com.chronos.message.Idao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.message.model.NotificationChannelPolicy;

public interface INotificationChannelPolicyRepository
		extends JpaRepository<NotificationChannelPolicy, String> {

	Optional<NotificationChannelPolicy> findByChannel(String channel);
}

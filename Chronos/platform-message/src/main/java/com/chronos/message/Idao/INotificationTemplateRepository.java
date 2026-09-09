package com.chronos.message.Idao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.chronos.message.model.NotificationTemplate;

public interface INotificationTemplateRepository
		extends JpaRepository<NotificationTemplate, String>, JpaSpecificationExecutor<NotificationTemplate> {

	Optional<NotificationTemplate> findByTemplateCodeAndChannel(String templateCode, String channel);
}

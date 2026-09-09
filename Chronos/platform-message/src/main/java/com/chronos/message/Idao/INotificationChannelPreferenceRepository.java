package com.chronos.message.Idao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.message.model.NotificationChannelPreference;

public interface INotificationChannelPreferenceRepository
		extends JpaRepository<NotificationChannelPreference, String> {

	List<NotificationChannelPreference> findByUsernameOrderByChannelAsc(String username);

	Optional<NotificationChannelPreference> findByUsernameAndChannel(String username, String channel);
}

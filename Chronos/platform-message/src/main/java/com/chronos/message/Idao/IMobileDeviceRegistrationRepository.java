package com.chronos.message.Idao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.message.model.MobileDeviceRegistration;

public interface IMobileDeviceRegistrationRepository
		extends JpaRepository<MobileDeviceRegistration, String> {
	Optional<MobileDeviceRegistration> findByUsernameAndPlatformAndInstallationId(
			String username,
			String platform,
			String installationId);

	List<MobileDeviceRegistration> findByUsernameAndEnabledTrueOrderByLastSeenAtDesc(String username);
}

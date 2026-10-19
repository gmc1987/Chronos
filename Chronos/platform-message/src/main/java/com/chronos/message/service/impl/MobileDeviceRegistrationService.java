package com.chronos.message.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.message.Idao.IMobileDeviceRegistrationRepository;
import com.chronos.message.model.MobileDeviceRegistration;
import com.chronos.security.SecretEncryptionProvider;

/** Stores provider tokens encrypted and never returns their plaintext or ciphertext. */
@Service
public class MobileDeviceRegistrationService {
	private final IMobileDeviceRegistrationRepository devices;
	private final SecretEncryptionProvider encryption;

	public MobileDeviceRegistrationService(
			IMobileDeviceRegistrationRepository devices,
			SecretEncryptionProvider encryption) {
		this.devices = devices;
		this.encryption = encryption;
	}

	@Transactional
	public DeviceView register(String username, String installationId, RegistrationCommand command) {
		String platform = normalizedPlatform(command.platform());
		String cleanInstallationId = required(installationId, "installationId", 128);
		String providerToken = required(command.providerToken(), "providerToken", 4096);
		if (providerToken.length() < 20) {
			throw new IllegalArgumentException("providerToken 格式无效");
		}
		MobileDeviceRegistration device = devices
				.findByUsernameAndPlatformAndInstallationId(username, platform, cleanInstallationId)
				.orElseGet(MobileDeviceRegistration::new);
		device.setUsername(username);
		device.setPlatform(platform);
		device.setInstallationId(cleanInstallationId);
		device.setProviderTokenCiphertext(encryption.encrypt(providerToken));
		device.setProviderTokenFingerprint(encryption.fingerprint(providerToken));
		device.setDeviceModel(optional(command.deviceModel(), 128));
		device.setAppVersion(optional(command.appVersion(), 64));
		device.setEnabled(true);
		device.setLastSeenAt(LocalDateTime.now());
		return view(devices.save(device));
	}

	@Transactional(readOnly = true)
	public List<DeviceView> mine(String username) {
		return devices.findByUsernameAndEnabledTrueOrderByLastSeenAtDesc(username).stream()
				.map(this::view)
				.toList();
	}

	@Transactional
	public void unregister(String username, String platform, String installationId) {
		devices.findByUsernameAndPlatformAndInstallationId(
				username, normalizedPlatform(platform), required(installationId, "installationId", 128))
				.ifPresent(device -> {
					device.setEnabled(false);
					device.setLastSeenAt(LocalDateTime.now());
					devices.save(device);
				});
	}

	private DeviceView view(MobileDeviceRegistration device) {
		return new DeviceView(
				device.getId(), device.getPlatform(), device.getInstallationId(),
				device.getDeviceModel(), device.getAppVersion(), device.getLastSeenAt());
	}

	private String normalizedPlatform(String value) {
		String platform = required(value, "platform", 16).toUpperCase(Locale.ROOT);
		if (!List.of("IOS", "ANDROID").contains(platform)) {
			throw new IllegalArgumentException("platform 仅支持 IOS 或 ANDROID");
		}
		return platform;
	}

	private String required(String value, String name, int maxLength) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " 不能为空");
		}
		String cleaned = value.trim();
		if (cleaned.length() > maxLength) {
			throw new IllegalArgumentException(name + " 长度超限");
		}
		return cleaned;
	}

	private String optional(String value, int maxLength) {
		return value == null || value.isBlank() ? null : value.trim().substring(0,
				Math.min(value.trim().length(), maxLength));
	}

	public record RegistrationCommand(
			String platform,
			String providerToken,
			String deviceModel,
			String appVersion) {
	}

	public record DeviceView(
			String id,
			String platform,
			String installationId,
			String deviceModel,
			String appVersion,
			LocalDateTime lastSeenAt) {
	}
}

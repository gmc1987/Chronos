package com.chronos.message.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.chronos.message.Idao.IMobileDeviceRegistrationRepository;
import com.chronos.message.model.MobileDeviceRegistration;
import com.chronos.message.service.impl.MobileDeviceRegistrationService;
import com.chronos.message.service.impl.MobileDeviceRegistrationService.RegistrationCommand;
import com.chronos.security.SecretEncryptionProvider;

class MobileDeviceRegistrationServiceTest {
	@Test
	void encryptsProviderTokenAndReturnsOnlySafeMetadata() {
		IMobileDeviceRegistrationRepository repository = mock(IMobileDeviceRegistrationRepository.class);
		SecretEncryptionProvider encryption = mock(SecretEncryptionProvider.class);
		when(repository.findByUsernameAndPlatformAndInstallationId("teacher", "IOS", "installation-1"))
				.thenReturn(Optional.empty());
		when(encryption.encrypt("0123456789abcdef0123456789abcdef")).thenReturn("ciphertext");
		when(encryption.fingerprint("0123456789abcdef0123456789abcdef")).thenReturn("fingerprint");
		when(repository.save(any(MobileDeviceRegistration.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		MobileDeviceRegistrationService service = new MobileDeviceRegistrationService(repository, encryption);
		var view = service.register("teacher", "installation-1", new RegistrationCommand(
				"ios", "0123456789abcdef0123456789abcdef", "iPhone", "1.0"));

		assertThat(view.platform()).isEqualTo("IOS");
		assertThat(view.installationId()).isEqualTo("installation-1");
		verify(encryption).encrypt("0123456789abcdef0123456789abcdef");
		verify(encryption).fingerprint("0123456789abcdef0123456789abcdef");
		verify(repository).save(any(MobileDeviceRegistration.class));
	}
}

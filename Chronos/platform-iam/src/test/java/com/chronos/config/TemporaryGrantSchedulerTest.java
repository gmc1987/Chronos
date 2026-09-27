package com.chronos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.ITemporaryGrantRepository;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.TemporaryGrant;

class TemporaryGrantSchedulerTest {
	@Test
	void doesNotActivateAnAlreadyExpiredApprovedGrant() {
		ITemporaryGrantRepository grants = mock(ITemporaryGrantRepository.class);
		IAdminUserRepository users = mock(IAdminUserRepository.class);
		TemporaryGrant expired = new TemporaryGrant();
		expired.setStatus("APPROVED");
		expired.setValidUntil(LocalDateTime.now().minusMinutes(1));
		when(grants.findByStatusAndValidUntilLessThanEqual(
				org.mockito.ArgumentMatchers.eq("APPROVED"),
				org.mockito.ArgumentMatchers.any(LocalDateTime.class))).thenReturn(List.of(expired));
		when(grants.findByStatusAndValidFromLessThanEqualAndValidUntilGreaterThan(
				org.mockito.ArgumentMatchers.eq("APPROVED"),
				org.mockito.ArgumentMatchers.any(LocalDateTime.class),
				org.mockito.ArgumentMatchers.any(LocalDateTime.class))).thenReturn(List.of());
		when(grants.findByStatusAndValidUntilLessThanEqual(
				org.mockito.ArgumentMatchers.eq("ACTIVE"),
				org.mockito.ArgumentMatchers.any(LocalDateTime.class))).thenReturn(List.of());

		new TemporaryGrantScheduler(grants, users).expireGrants();

		assertThat(expired.getStatus()).isEqualTo("EXPIRED");
		org.mockito.Mockito.verify(grants).save(expired);
	}
}

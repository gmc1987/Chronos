package com.chronos.education.scheduling.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class StudentGuardianRelationTest {
	@Test
	void onlyVerifiedUnrestrictedRelationWithValidConsentAllowsAccess() {
		StudentGuardianRelation relation = new StudentGuardianRelation();
		relation.setVerificationStatus("VERIFIED");
		relation.setRestrictionLevel("NONE");
		relation.setStudentConsentStatus("GRANTED");
		relation.setConsentExpiresAt(LocalDateTime.now().plusDays(30));

		assertThat(relation.isAccessAllowed()).isTrue();
		relation.setRestrictionLevel("NO_ACCESS");
		assertThat(relation.isAccessAllowed()).isFalse();
	}

	@Test
	void pendingOrExpiredConsentDoesNotGrantAccess() {
		StudentGuardianRelation relation = new StudentGuardianRelation();
		relation.setVerificationStatus("PENDING");
		relation.setRestrictionLevel("NONE");
		relation.setStudentConsentStatus("NOT_REQUIRED");
		assertThat(relation.isAccessAllowed()).isFalse();

		relation.setVerificationStatus("VERIFIED");
		relation.setStudentConsentStatus("GRANTED");
		relation.setConsentExpiresAt(LocalDateTime.now().minusMinutes(1));
		assertThat(relation.isAccessAllowed()).isFalse();
	}
}

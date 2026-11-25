package com.chronos.delivery;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("license")
class LicenseHealthIndicator implements HealthIndicator {
	private final CustomerLicense license;
	private final int expectedRevision;

	LicenseHealthIndicator(CustomerLicense license, @Value("${CHRONOS_DELIVERY_EXPECTED_REVISION:0}") int expectedRevision) {
		this.license = license;
		this.expectedRevision = expectedRevision;
	}

	@Override
	public Health health() {
		var status = license.status();
		return !license.customer() || (status.active() && (expectedRevision == 0 || status.revision() == expectedRevision))
				? Health.up().build() : Health.down().build();
	}
}

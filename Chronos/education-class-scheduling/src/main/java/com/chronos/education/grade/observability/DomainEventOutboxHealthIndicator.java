package com.chronos.education.grade.observability;

import com.chronos.education.grade.dao.DomainEventOutboxRepository;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component("domainEventOutbox")
public class DomainEventOutboxHealthIndicator implements HealthIndicator {
	private final DomainEventOutboxRepository repository;

	public DomainEventOutboxHealthIndicator(DomainEventOutboxRepository repository) {
		this.repository = repository;
	}

	@Override
	public Health health() {
		try {
			return Health.up()
					.withDetail("pending", repository.countByStatus("PENDING"))
					.withDetail("processing", repository.countByStatus("PROCESSING"))
					.withDetail("dead", repository.countByStatus("DEAD"))
					.build();
		} catch (DataAccessException failure) {
			return Health.down(failure).build();
		}
	}
}

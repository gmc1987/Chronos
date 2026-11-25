package com.chronos.delivery;

import com.chronos.ChronosEducationApplication;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.AnnotatedTypeMetadata;

final class DeliveryMode {
	private DeliveryMode() {
	}

	static String resolve(Environment environment) {
		var classLocation = ChronosEducationApplication.class.getResource("ChronosEducationApplication.class");
		boolean sourceLaunch = classLocation != null && "file".equals(classLocation.getProtocol());
		return resolve(environment, sourceLaunch);
	}

	static String resolve(Environment environment, boolean sourceLaunch) {
		String mode = environment.getProperty("chronos.delivery.mode", "auto");
		if (!"auto".equals(mode)) {
			return mode;
		}
		boolean deployedProfile = environment.acceptsProfiles(Profiles.of("docker", "prod", "production"));
		return sourceLaunch && !deployedProfile ? "issuer" : "standard";
	}

	static final class IssuerCondition implements Condition {
		@Override
		public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
			return "issuer".equals(resolve(context.getEnvironment()));
		}
	}
}

package com.chronos.delivery;

import java.lang.reflect.Method;
import java.util.Set;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "chronos.delivery.mode", havingValue = "customer")
class DeliveryScheduledTasks {
	@Bean
	static BeanFactoryPostProcessor licenseBoundScheduledTasks() {
		return factory -> {
			if (!(factory instanceof BeanDefinitionRegistry registry)) return;
			String name = TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME;
			if (!registry.containsBeanDefinition(name)) return;
			registry.removeBeanDefinition(name);
			var definition = new RootBeanDefinition(LicenseScheduledProcessor.class);
			definition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
			registry.registerBeanDefinition(name, definition);
		};
	}

	/** Customer deployments schedule only shared maintenance and tasks in licensed modules. */
	static final class LicenseScheduledProcessor extends ScheduledAnnotationBeanPostProcessor {
		private static final Set<String> SHARED = Set.of("IamLifecycleScheduler", "TemporaryGrantScheduler",
				"OidcAuthorizationStateCleanup", "ManagedFileDraftCleanupScheduler");
		private BeanFactory beans;

		@Override
		public void setBeanFactory(BeanFactory beanFactory) {
			super.setBeanFactory(beanFactory);
			beans = beanFactory;
		}

		@Override
		protected Runnable createRunnable(Object target, Method method, String qualifier) {
			var run = super.createRunnable(target, method, qualifier);
			String name = method.getDeclaringClass().getSimpleName();
			return () -> {
				if (SHARED.contains(name)) {
					run.run();
					return;
				}
				String module = switch (name) {
					case "OfficialHolidayImportService" -> "TERM_CALENDAR";
					case "FacilityDispatchScheduler" -> "FACILITY_REPAIR";
					case "MeetingLifecycleScheduler" -> "CAMPUS_MEETINGS";
					case "ExamCheckInEscalationScheduler" -> "EDU_EXAM";
					case "TeacherPersonnelScheduler" -> "EDU_TEACHER_PERSONNEL";
					case "ScheduleGenerationJobService" -> "EDU_TEACHING_OPERATIONS";
					case "KnowledgeIndexScheduler" -> "AI_KNOWLEDGE";
					case "IntegrationScheduler" -> "THIRD_PARTY_INTEGRATIONS";
					case "PublicationDeliveryScheduler", "PublicationLifecycleScheduler" -> "CAMPUS_PUBLICATIONS";
					default -> "";
				};
				if (!module.isEmpty() && beans.getBean(CustomerLicense.class).moduleAllowed(module)) run.run();
			};
		}
	}
}

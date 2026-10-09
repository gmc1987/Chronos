package com.chronos.education.scheduling.service;

import com.chronos.workflow.WorkflowStartValidator;
import java.util.Map;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** 确保通用流程入口也不能绕过出差身份及财务强制校验。 */
@Component
public class BusinessTripStartValidator implements WorkflowStartValidator {
	private final BusinessTripService trips;

	public BusinessTripStartValidator(@Lazy BusinessTripService trips) {
		this.trips = trips;
	}

	@Override
	public boolean supports(String flowCode) {
		return BusinessTripService.FLOW_CODE.equals(flowCode);
	}

	@Override
	public void validate(String actor, Map<String, Object> formData) {
		trips.validateWorkflowStart(actor, formData);
	}
}

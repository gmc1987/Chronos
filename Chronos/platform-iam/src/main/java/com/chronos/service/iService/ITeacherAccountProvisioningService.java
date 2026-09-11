package com.chronos.service.iService;

import com.chronos.model.dto.TeacherAccountProvisioning;

/** Public IAM boundary used by education modules; education must not use IAM repositories. */
public interface ITeacherAccountProvisioningService {
	TeacherAccountProvisioning provision(String employeeId, String displayName);
	TeacherAccountProvisioning find(String employeeId);
}

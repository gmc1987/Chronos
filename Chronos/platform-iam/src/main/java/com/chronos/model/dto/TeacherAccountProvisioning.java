package com.chronos.model.dto;

/**
 * Result of provisioning an education teacher account.  The generated
 * bootstrap password is deliberately not part of this contract.
 */
public record TeacherAccountProvisioning(String username, String employeeId, boolean created) {
}

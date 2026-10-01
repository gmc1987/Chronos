package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.DataReportTaskRepository;
import com.chronos.file.service.ManagedFileAccessPolicy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 报表文件沿用任务创建人的教育数据范围，不把文件权限扩大成全局读权限。 */
@Component
@Transactional(readOnly = true)
public class EducationDataReportFileAccessPolicy implements ManagedFileAccessPolicy {
	private static final String REPORT = "EDUCATION_DATA_REPORT";

	private final DataReportTaskRepository reports;
	private final EducationDataScopeService scopes;

	public EducationDataReportFileAccessPolicy(
			DataReportTaskRepository reports,
			EducationDataScopeService scopes) {
		this.reports = reports;
		this.scopes = scopes;
	}

	@Override
	public boolean canRead(String username, String businessType, String businessId) {
		return REPORT.equals(businessType)
				&& reports.findById(businessId)
						.map(task -> canAccessTask(username, task.getRequestedBy(), task.getCampusId()))
						.orElse(false);
	}

	@Override
	public boolean canWrite(String username, String businessType, String businessId) {
		return REPORT.equals(businessType)
				&& reports.findById(businessId)
						.map(task -> username.equals(task.getRequestedBy()))
						.orElse(false);
	}

	private boolean canAccessTask(String username, String owner, String campusId) {
		if (username.equals(owner)) {
			return true;
		}
		var scope = scopes.resolve(username);
		return scope.fullAccess() || (campusId != null
				&& !campusId.isBlank()
				&& scope.campusIds().contains(campusId));
	}
}

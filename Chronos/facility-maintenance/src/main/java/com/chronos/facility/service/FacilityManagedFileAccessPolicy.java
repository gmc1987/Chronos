package com.chronos.facility.service;

import org.springframework.stereotype.Component;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.facility.dao.RepairOrderRepository;
import com.chronos.file.service.ManagedFileAccessPolicy;

@Component
public class FacilityManagedFileAccessPolicy implements ManagedFileAccessPolicy {
	private final RepairOrderRepository orders;
	private final IAdminUserRepository users;

	public FacilityManagedFileAccessPolicy(RepairOrderRepository orders, IAdminUserRepository users) {
		this.orders = orders;
		this.users = users;
	}

	@Override
	public boolean canRead(String username, String businessType, String businessId) {
		if (!supported(businessType) || businessId == null) return false;
		return orders.findById(businessId).map(order -> username.equals(order.getReporterUsername())
				|| username.equals(order.getTechnicianUsername())
				|| users.findActiveUsernamesByPermissionCode("facility:repair:view").contains(username)
				|| users.findActiveUsernamesByPermissionCode("facility:repair:manage").contains(username)).orElse(false);
	}

	@Override
	public boolean canWrite(String username, String businessType, String businessId) {
		if (!supported(businessType) || businessId == null) return false;
		return orders.findById(businessId).map(order -> {
			if (FacilityMaintenanceService.REPORT_FILE_TYPE.equals(businessType)) {
				return username.equals(order.getReporterUsername()) && "PENDING_DISPATCH".equals(order.getStatus());
			}
			return username.equals(order.getTechnicianUsername())
					&& java.util.Set.of("ACCEPTED", "IN_PROGRESS", "WAITING_MATERIAL").contains(order.getStatus());
		}).orElse(false);
	}

	private boolean supported(String type) {
		return FacilityMaintenanceService.REPORT_FILE_TYPE.equals(type)
				|| FacilityMaintenanceService.COMPLETION_FILE_TYPE.equals(type);
	}
}

package com.chronos.file.service;

/** 业务模块通过该扩展点决定非文件所有者是否可读取附件。 */
public interface ManagedFileAccessPolicy {
	boolean canRead(String username, String businessType, String businessId);

	default boolean canWrite(String username, String businessType, String businessId) {
		return false;
	}

	/**
	 * 删除已有文件时携带文件标识，让业务模块可以执行字段级授权。
	 * 默认委托给原有业务级判断，兼容不需要字段权限的接入模块。
	 */
	default boolean canWrite(
			String username,
			String businessType,
			String businessId,
			String fileId) {
		return canWrite(username, businessType, businessId);
	}
}

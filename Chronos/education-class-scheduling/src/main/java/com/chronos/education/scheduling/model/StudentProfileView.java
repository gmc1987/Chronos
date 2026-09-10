package com.chronos.education.scheduling.model;

import java.time.LocalDateTime;

/**
 * 学生档案只读视图。
 *
 * <p>禁止控制器直接序列化学生实体，避免新增隐私字段后被列表接口自动暴露。</p>
 */
public record StudentProfileView(
		String id,
		String studentNo,
		String studentName,
		String gender,
		Integer gradeYear,
		String gradeId,
		String majorId,
		String administrativeClassId,
		String enrollmentStatus,
		String phone,
		String createBy,
		LocalDateTime createTime,
		String lastUpdateBy,
		LocalDateTime lastUpdateTime) {
	public static StudentProfileView from(
			StudentProfile student,
			boolean privacyVisible) {
		return new StudentProfileView(
				student.getId(),
				student.getStudentNo(),
				student.getStudentName(),
				student.getGender(),
				student.getGradeYear(),
				student.getGradeId(),
				student.getMajorId(),
				student.getAdministrativeClassId(),
				student.getEnrollmentStatus(),
				privacyVisible ? student.getPhone() : maskPhone(student.getPhone()),
				student.getCreateBy(),
				student.getCreateTime(),
				student.getLastUpdateBy(),
				student.getLastUpdateTime());
	}

	private static String maskPhone(String phone) {
		if (phone == null || phone.isBlank()) {
			return phone;
		}
		if (phone.length() <= 4) {
			return "****";
		}
		if (phone.length() <= 7) {
			return phone.substring(0, 2) + "****";
		}
		return phone.substring(0, 3)
				+ "****"
				+ phone.substring(phone.length() - 4);
	}
}

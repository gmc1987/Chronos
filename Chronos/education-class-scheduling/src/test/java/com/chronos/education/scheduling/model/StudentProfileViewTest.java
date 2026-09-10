package com.chronos.education.scheduling.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StudentProfileViewTest {
	@Test
	void masksPhoneWithoutPrivacyPermission() {
		StudentProfile student = student("13900000001");

		StudentProfileView view = StudentProfileView.from(student, false);

		assertThat(view.phone()).isEqualTo("139****0001");
	}

	@Test
	void returnsFullPhoneWithPrivacyPermission() {
		StudentProfile student = student("13900000001");

		StudentProfileView view = StudentProfileView.from(student, true);

		assertThat(view.phone()).isEqualTo("13900000001");
	}

	@Test
	void doesNotLeakShortPhoneValues() {
		StudentProfile student = student("1234");

		StudentProfileView view = StudentProfileView.from(student, false);

		assertThat(view.phone()).isEqualTo("****");
	}

	private StudentProfile student(String phone) {
		StudentProfile student = new StudentProfile();
		student.setPhone(phone);
		return student;
	}
}

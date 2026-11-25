package com.chronos.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LicenseDocumentTest {
	@TempDir
	Path keys;

	@Test
	void keepsUnverifiedProductsMappedButUnissuableAndSharedReadsExact() {
		var pending = DeliveryCatalog.PRODUCTS.stream().filter(product -> !product.issuable()).toList();
		assertThat(pending).allSatisfy(product -> {
			assertThat(product.endpoints()).isNotEmpty();
			assertThat(product.pendingReason()).isNotBlank();
			assertThatThrownBy(() -> DeliveryCatalog.resolve(List.of(product.code())))
					.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("暂不可签发");
		});
		var housing = DeliveryCatalog.require("EDU_ACCOMMODATION");
		assertThat(housing.allows("GET", "/admin/education/terms")).isTrue();
		assertThat(housing.allows("POST", "/admin/education/terms")).isFalse();
		assertThat(housing.allows("GET", "/admin/education/terms/123/archive-readiness")).isFalse();
		var school = DeliveryCatalog.require("EDU_HOME_SCHOOL");
		assertThat(DeliveryCatalog.resolve(List.of(school.code()))).containsExactly("EDU_HOME_SCHOOL");
		assertThat(school.allows("POST", "/admin/education/home-school/setup/roster")).isTrue();
		assertThat(school.allows("GET", "/admin/education/parents")).isFalse();
		assertThat(school.allows("GET", "/admin/education/students")).isFalse();
		assertThat(school.allows("GET", "/portal/education/family/notices")).isTrue();
		assertThat(school.allows("GET", "/portal/education/family/grades")).isFalse();
	}

	@Test
	void teacherPersonnelLicenseUsesDedicatedWorkflowEntrypoints() {
		var personnel = DeliveryCatalog.require("EDU_TEACHER_PERSONNEL");
		assertThat(DeliveryCatalog.resolve(List.of(personnel.code()))).containsExactly("EDU_TEACHER_PERSONNEL");
		assertThat(personnel.allows("POST", "/admin/education/teacher-personnel/requests")).isTrue();
		assertThat(personnel.allows("POST", "/portal/education/teacher-personnel/resignations")).isTrue();
		assertThat(personnel.allows("GET", "/portal/education/teacher-personnel/resignations")).isFalse();
		assertThat(personnel.allows("GET", "/admin/iam/employees")).isFalse();
		assertThat(personnel.allows("GET", "/workflow-tasks/pending")).isFalse();
		assertThat(personnel.allows("POST", "/workflows/example/start")).isFalse();
	}

	@Test
	void allowsStandaloneVenueWithoutOpeningLegacyWorkflowOrScheduling() {
		var venue = DeliveryCatalog.require("CAMPUS_VENUE");
		assertThat(DeliveryCatalog.resolve(List.of(venue.code()))).containsExactly("CAMPUS_VENUE");
		assertThat(venue.allows("POST", "/portal/education/classroom-reservations")).isTrue();
		assertThat(venue.allows("POST", "/admin/education/classroom-reservations/desk/id/review")).isTrue();
		assertThat(venue.allows("POST", "/admin/education/classroom-reservations/id/retry")).isFalse();
		assertThat(venue.allows("GET", "/portal/education/academic-terms/options")).isFalse();
		assertThat(venue.allows("POST", "/admin/education/terms")).isFalse();
		assertThat(venue.allows("GET", "/admin/education/schedules")).isFalse();
	}

	@Test
	void verifiesSignedModulesAndRejectsTamperingAndForeignInstances() throws Exception {
		var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
		var privatePath = keys.resolve("issuer.pem");
		write(privatePath, "PRIVATE KEY", pair.getPrivate().getEncoded());
		write(keys.resolve("release.pem"), "PUBLIC KEY", pair.getPublic().getEncoded());
		var mapper = new ObjectMapper();
		var service = new LicenseDocument(mapper);
		var instance = UUID.randomUUID();
		var modules = List.copyOf(DeliveryCatalog.resolve(List.of("FACILITY_GOVERNANCE")));
		var signed = service.sign(instance, 2, modules, "release", privatePath);
		assertThat(service.verify(signed.document(), instance, keys).modules())
				.containsExactly("FACILITY_GOVERNANCE", "FACILITY_REPAIR");
		var meeting = service.sign(instance, 3, List.copyOf(DeliveryCatalog.resolve(List.of("CAMPUS_MEETINGS"))),
				"release", privatePath);
		assertThat(service.verify(meeting.document(), instance, keys).modules()).containsExactly("CAMPUS_MEETINGS");
		assertThatThrownBy(() -> DeliveryCatalog.resolve(List.of("ASSET_MANAGEMENT")))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("暂不可签发");
		var pending = service.sign(instance, 4, List.of("ASSET_MANAGEMENT"), "release", privatePath);
		assertThatThrownBy(() -> service.verify(pending.document(), instance, keys))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.verify(signed.document(), UUID.randomUUID(), keys))
				.isInstanceOf(IllegalArgumentException.class);
		var envelope = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(signed.document());
		envelope.put("payload", Base64.getUrlEncoder().withoutPadding().encodeToString("{}".getBytes()));
		assertThatThrownBy(() -> service.verify(envelope.toString(), instance, keys))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private void write(Path path, String type, byte[] bytes) throws Exception {
		Files.writeString(path, "-----BEGIN " + type + "-----\n" + Base64.getEncoder().encodeToString(bytes)
				+ "\n-----END " + type + "-----\n");
	}
}

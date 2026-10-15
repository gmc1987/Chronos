package com.chronos.education.supervision.service;

import com.chronos.education.supervision.model.SupervisionAssignment;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 由授课教师在课堂签发短时 QR 凭证。凭证只适用于指定任务和授课教师，
 * 服务端验签和任务状态校验共同防止客户端自行声明签到位置或时间。
 */
@Component
@ConditionalOnProperty(name = "chronos.supervision.qr.secret")
public class TeacherIssuedSupervisionQrProofProvider implements SupervisionCheckInProofProvider {
	public static final String PROVIDER_ID = "TEACHER_QR_V1";
	private static final long VALID_SECONDS = 300;
	private final byte[] secret;
	private final SecureRandom random = new SecureRandom();

	public TeacherIssuedSupervisionQrProofProvider(
			@Value("${chronos.supervision.qr.secret}") String secret) {
		if (secret == null || secret.length() < 32) {
			throw new IllegalArgumentException("督导二维码签名密钥长度至少为 32 字符");
		}
		this.secret = secret.getBytes(StandardCharsets.UTF_8);
	}

	public Proof issue(SupervisionAssignment assignment, Instant issuedAt) {
		byte[] nonce = new byte[16];
		random.nextBytes(nonce);
		String payload = String.join("|", "v1", assignment.getId(), assignment.getTeacherId(),
				Long.toString(issuedAt.plusSeconds(VALID_SECONDS).getEpochSecond()),
				Base64.getUrlEncoder().withoutPadding().encodeToString(nonce));
		String encoded = Base64.getUrlEncoder().withoutPadding()
				.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
		return new Proof(PROVIDER_ID, encoded + "." + encode(sign(payload)));
	}

	@Override
	public VerificationResult verify(VerificationRequest request) {
		Proof proof = request.proof();
		if (proof == null || !PROVIDER_ID.equals(proof.providerId())) {
			return rejected(VerificationCode.INVALID_PROOF);
		}
		try {
			String[] tokenParts = proof.signedProof().split("\\.", -1);
			if (tokenParts.length != 2) return rejected(VerificationCode.INVALID_PROOF);
			String payload = new String(Base64.getUrlDecoder().decode(tokenParts[0]), StandardCharsets.UTF_8);
			byte[] suppliedSignature = Base64.getUrlDecoder().decode(tokenParts[1]);
			if (!MessageDigest.isEqual(sign(payload), suppliedSignature)) {
				return rejected(VerificationCode.INVALID_PROOF);
			}
			String[] fields = payload.split("\\|", -1);
			if (fields.length != 5 || !"v1".equals(fields[0])) return rejected(VerificationCode.INVALID_PROOF);
			if (!fields[1].equals(request.assignment().getId())
					|| !fields[2].equals(request.assignment().getTeacherId())) {
				return rejected(VerificationCode.SCOPE_MISMATCH);
			}
			Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(fields[3]));
			if (!expiresAt.isAfter(request.verifiedAt())) return rejected(VerificationCode.PROOF_EXPIRED);
			return new VerificationResult(VerificationCode.VERIFIED,
					fields[1] + ":" + fields[4], expiresAt);
		} catch (RuntimeException exception) {
			return rejected(VerificationCode.INVALID_PROOF);
		}
	}

	private byte[] sign(String payload) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret, "HmacSHA256"));
			return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
		} catch (Exception exception) {
			throw new IllegalStateException("督导二维码签名服务不可用", exception);
		}
	}

	private String encode(byte[] value) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
	}

	private VerificationResult rejected(VerificationCode code) {
		return new VerificationResult(code, null, null);
	}
}

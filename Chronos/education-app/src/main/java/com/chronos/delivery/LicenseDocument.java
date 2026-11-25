package com.chronos.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class LicenseDocument {
	private static final int LIMIT = 65536;
	private final ObjectMapper mapper;

	LicenseDocument(ObjectMapper mapper) {
		this.mapper = mapper;
	}

	record Payload(int format, UUID deploymentId, int revision, UUID licenseId,
			String issuedAt, List<String> modules) {
	}

	record Signed(String document, Payload payload) {
	}

	Signed sign(UUID deployment, int revision, List<String> modules, String keyId, Path privateKey) {
		var payload = new Payload(1, deployment, revision, UUID.randomUUID(), Instant.now().toString(), modules);
		try {
			if (!keyId.matches("[A-Za-z0-9._-]{1,64}")) throw new IllegalArgumentException("签名密钥编号无效");
			var header = mapper.createObjectNode().put("alg", "Ed25519").put("kid", keyId)
					.put("typ", "chronos-license+jws");
			String protectedHeader = encode(mapper.writeValueAsBytes(header));
			String body = encode(mapper.writeValueAsBytes(payload));
			var signer = Signature.getInstance("Ed25519");
			signer.initSign(KeyFactory.getInstance("Ed25519")
					.generatePrivate(new PKCS8EncodedKeySpec(readKey(privateKey, "PRIVATE KEY"))));
			signer.update(signingInput(protectedHeader, body));
			var envelope = mapper.createObjectNode().put("protected", protectedHeader)
					.put("payload", body).put("signature", encode(signer.sign()));
			return new Signed(mapper.writeValueAsString(envelope), payload);
		} catch (IOException | GeneralSecurityException failure) {
			throw new IllegalStateException("无法使用签发密钥生成授权", failure);
		}
	}

	Payload verify(String document, UUID expectedInstance, Path publicKeys) {
		try {
			if (document == null || document.length() > LIMIT) throw invalid();
			var envelope = mapper.readTree(document);
			String protectedHeader = envelope.path("protected").asText();
			String body = envelope.path("payload").asText();
			var header = mapper.readTree(decode(protectedHeader));
			String keyId = header.path("kid").asText();
			if (!"Ed25519".equals(header.path("alg").asText())
					|| !"chronos-license+jws".equals(header.path("typ").asText())
					|| header.has("crit") || !keyId.matches("[A-Za-z0-9._-]{1,64}")) throw invalid();
			var verifier = Signature.getInstance("Ed25519");
			verifier.initVerify(KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(
					readKey(publicKeys.resolve(keyId + ".pem"), "PUBLIC KEY"))));
			verifier.update(signingInput(protectedHeader, body));
			if (!verifier.verify(decode(envelope.path("signature").asText()))) throw invalid();
			Payload payload = mapper.readValue(decode(body), Payload.class);
			if (payload.format() != 1 || !expectedInstance.equals(payload.deploymentId())
					|| payload.revision() < 1 || payload.licenseId() == null || payload.modules() == null
					|| !List.copyOf(DeliveryCatalog.resolve(payload.modules())).equals(payload.modules())
					|| Instant.parse(payload.issuedAt()).isAfter(Instant.now().plusSeconds(300))) throw invalid();
			return payload;
		} catch (IOException | GeneralSecurityException | RuntimeException failure) {
			throw invalid();
		}
	}

	static String readDocument(Path path) throws IOException {
		try (var stream = Files.newInputStream(path)) {
			byte[] content = stream.readNBytes(LIMIT + 1);
			if (content.length > LIMIT) throw invalid();
			return new String(content, StandardCharsets.UTF_8);
		}
	}

	private byte[] readKey(Path path, String type) throws IOException {
		return Base64.getMimeDecoder().decode(readDocument(path)
				.replace("-----BEGIN " + type + "-----", "").replace("-----END " + type + "-----", ""));
	}

	private static byte[] signingInput(String header, String payload) {
		return (header + "." + payload).getBytes(StandardCharsets.US_ASCII);
	}

	private static byte[] decode(String value) {
		return Base64.getUrlDecoder().decode(value);
	}

	private static String encode(byte[] value) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
	}

	private static IllegalArgumentException invalid() {
		return new IllegalArgumentException("授权文件无效、签名密钥不受信任或与部署实例不匹配");
	}
}

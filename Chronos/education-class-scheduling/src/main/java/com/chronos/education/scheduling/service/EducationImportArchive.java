package com.chronos.education.scheduling.service;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.chronos.file.model.ManagedFileView;
import com.chronos.file.service.ManagedFileAccessPolicy;
import com.chronos.file.service.ManagedFileService;
import com.chronos.file.service.ManagedFileSecurityValidator;
import com.chronos.file.service.ManagedFileService.FileContent;

@Component
public class EducationImportArchive implements ManagedFileAccessPolicy {
	private static final String SOURCE = "EDUCATION_IMPORT_SOURCE";
	private static final long MAX_SIZE = 5L * 1024 * 1024;
	private final ManagedFileService files;
	private final ManagedFileSecurityValidator validator;

	public EducationImportArchive(@Lazy ManagedFileService files, ManagedFileSecurityValidator validator) {
		this.files = files;
		this.validator = validator;
	}

	@Override
	public boolean restricted(String businessType) {
		return SOURCE.equals(businessType);
	}

	@Override
	public boolean canRead(String username, String businessType, String businessId) {
		return false;
	}

	public byte[] validate(MultipartFile file) throws IOException {
		return validate(file, MAX_SIZE);
	}

	public byte[] validate(MultipartFile file, long maxSize) throws IOException {
		if (file == null || file.isEmpty() || file.getSize() > maxSize
				|| file.getOriginalFilename() == null
				|| !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
			throw new IllegalArgumentException("请选择不超过 " + (maxSize / (1024 * 1024)) + " MB 的 XLSX 文件");
		}
		byte[] content = file.getBytes();
		validator.validate(file.getOriginalFilename(), content);
		return content;
	}

	public void validateCsv(MultipartFile file) throws IOException {
		if (file == null || file.isEmpty() || file.getSize() > 2_000_000L
				|| file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".csv")) {
			throw new IllegalArgumentException("请选择不超过 2 MB 的 CSV 文件");
		}
		validator.validate(file.getOriginalFilename(), file.getBytes());
	}

	public ManagedFileView store(String batchId, MultipartFile file, Authentication actor) {
		return files.uploadRestricted(file, SOURCE, batchId, actor);
	}

	public FileContent read(String batchId, String fileId, String digest, Authentication actor) {
		if (fileId == null) throw new IllegalArgumentException("该历史批次未归档原文件");
		FileContent source = files.readRestricted(fileId, SOURCE, batchId, actor);
		if (!sha256(source.content()).equals(digest)) {
			throw new IllegalStateException("归档文件校验失败，请重新预检");
		}
		return source;
	}

	public static String sha256(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}
}

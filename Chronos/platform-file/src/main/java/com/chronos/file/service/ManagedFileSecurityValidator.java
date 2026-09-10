package com.chronos.file.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Component;

/**
 * 文件入口的基础安全校验。这里不信任浏览器上传的 MIME，而是校验文件签名，
 * 并拒绝 Office 宏、压缩炸弹以及 PDF 主动内容。生产环境仍可在此组件前接入杀毒引擎。
 */
@Component
public class ManagedFileSecurityValidator {
	private static final long MAX_EXPANDED_SIZE = 100L * 1024 * 1024;
	private static final int MAX_ZIP_ENTRIES = 2_000;
	private static final byte[] ZIP_MAGIC = { 0x50, 0x4B, 0x03, 0x04 };
	private static final byte[] OLE_MAGIC = {
			(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
			(byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
	};
	private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
			Map.entry("pdf", "application/pdf"),
			Map.entry("doc", "application/msword"),
			Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
			Map.entry("xls", "application/vnd.ms-excel"),
			Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
			Map.entry("txt", "text/plain"),
			Map.entry("md", "text/markdown"),
			Map.entry("png", "image/png"),
			Map.entry("jpg", "image/jpeg"),
			Map.entry("jpeg", "image/jpeg"));

	public String validate(String filename, byte[] content) {
		String extension = extension(filename);
		if (containsEicarSignature(content)) {
			throw new IllegalArgumentException("文件安全检测未通过");
		}

		switch (extension) {
		case "pdf" -> validatePdf(content);
		case "docx", "xlsx" -> validateOpenXml(content);
		case "doc", "xls" -> requireMagic(content, OLE_MAGIC);
		case "png" -> requireMagic(content, new byte[] {
				(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
		});
		case "jpg", "jpeg" -> requireMagic(content, new byte[] {
				(byte) 0xFF, (byte) 0xD8, (byte) 0xFF
		});
		case "txt", "md" -> validateText(content);
		default -> throw new IllegalArgumentException("不支持的文件类型");
		}
		return CONTENT_TYPES.get(extension);
	}

	private void validatePdf(byte[] content) {
		requireMagic(content, "%PDF-".getBytes(StandardCharsets.US_ASCII));
		String source = new String(content, StandardCharsets.ISO_8859_1);
		if (source.contains("/JavaScript")
				|| source.contains("/JS")
				|| source.contains("/Launch")
				|| source.contains("/EmbeddedFile")) {
			throw new IllegalArgumentException("PDF 包含不允许的主动内容");
		}
	}

	private void validateOpenXml(byte[] content) {
		requireMagic(content, ZIP_MAGIC);
		long expandedSize = 0;
		int entries = 0;
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
			ZipEntry entry;
			byte[] buffer = new byte[8_192];
			while ((entry = zip.getNextEntry()) != null) {
				entries += 1;
				if (entries > MAX_ZIP_ENTRIES) {
					throw new IllegalArgumentException("Office 文件条目数量超过安全限制");
				}
				String name = entry.getName().toLowerCase(Locale.ROOT);
				if (name.endsWith("vbaproject.bin")) {
					throw new IllegalArgumentException("Office 文件包含不允许的宏");
				}
				int read;
				while ((read = zip.read(buffer)) != -1) {
					expandedSize += read;
					if (expandedSize > MAX_EXPANDED_SIZE) {
						throw new IllegalArgumentException("Office 文件解压后超过安全限制");
					}
				}
			}
		} catch (IllegalArgumentException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new IllegalArgumentException("Office 文件结构无效", exception);
		}
	}

	private void validateText(byte[] content) {
		for (byte value : content) {
			if (value == 0) {
				throw new IllegalArgumentException("文本文件内容无效");
			}
		}
	}

	private void requireMagic(byte[] content, byte[] expected) {
		if (content.length < expected.length) {
			throw new IllegalArgumentException("文件内容与扩展名不匹配");
		}
		for (int index = 0; index < expected.length; index += 1) {
			if (content[index] != expected[index]) {
				throw new IllegalArgumentException("文件内容与扩展名不匹配");
			}
		}
	}

	private boolean containsEicarSignature(byte[] content) {
		String source = new String(content, StandardCharsets.ISO_8859_1);
		return source.contains("EICAR-STANDARD-ANTIVIRUS-TEST-FILE");
	}

	private String extension(String filename) {
		int separator = filename.lastIndexOf('.');
		return separator < 0
				? ""
				: filename.substring(separator + 1).toLowerCase(Locale.ROOT);
	}
}

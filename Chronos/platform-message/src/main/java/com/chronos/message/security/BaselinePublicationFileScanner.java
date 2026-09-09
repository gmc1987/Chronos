package com.chronos.message.security;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Component;

@Component
public class BaselinePublicationFileScanner implements PublicationFileScanner {
	@Override
	public void scan(byte[] content, String filename, String contentType) {
		String ascii = new String(content, StandardCharsets.ISO_8859_1);
		if (ascii.contains("EICAR-STANDARD-ANTIVIRUS-TEST-FILE")) {
			throw new IllegalArgumentException("文件未通过病毒扫描");
		}
		if (filename.toLowerCase(Locale.ROOT).endsWith(".pdf")
				&& (ascii.contains("/JavaScript") || ascii.contains("/Launch"))) {
			throw new IllegalArgumentException("PDF包含脚本或外部启动动作，禁止上传");
		}
		if (filename.toLowerCase(Locale.ROOT).endsWith(".docx")) {
			checkDocxEntries(content);
		}
	}

	private void checkDocxEntries(byte[] content) {
		try (var zip = new ZipInputStream(new ByteArrayInputStream(content))) {
			long expanded = 0;
			for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
				String name = entry.getName().toLowerCase(Locale.ROOT);
				if (name.endsWith("vbaproject.bin") || name.contains("/embeddings/")) {
					throw new IllegalArgumentException("Word文档包含宏或嵌入对象，禁止上传");
				}
				expanded += Math.max(entry.getSize(), 0);
				if (expanded > 200L * 1024 * 1024) {
					throw new IllegalArgumentException("Word文档解压体积异常");
				}
			}
		} catch (IllegalArgumentException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new IllegalArgumentException("Word文档结构无效", exception);
		}
	}
}

package com.chronos.knowledge.service;

import com.chronos.commons.utils.DocToMarkdownUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** 按受支持的文档格式提取可检索文本，并对扫描件给出明确诊断。 */
@Component
public class KnowledgeDocumentTextExtractor {
	public String extract(MultipartFile file, String filename) throws IOException {
		String lower = filename == null
				? ""
				: filename.toLowerCase(Locale.ROOT);
		if (lower.endsWith(".txt") || lower.endsWith(".md")) {
			return new String(file.getBytes(), StandardCharsets.UTF_8);
		}
		if (lower.endsWith(".doc") || lower.endsWith(".docx")) {
			return DocToMarkdownUtil.convert(file.getInputStream(), filename);
		}
		if (lower.endsWith(".pdf")) {
			return extractPdf(file);
		}
		throw new IllegalArgumentException(
				"仅支持 TXT、Markdown、DOC、DOCX 和 PDF 文档");
	}

	private String extractPdf(MultipartFile file) throws IOException {
		try (PDDocument document = PDDocument.load(file.getInputStream())) {
			if (document.isEncrypted()) {
				throw new IllegalArgumentException("暂不支持加密 PDF 文档");
			}
			String content = new PDFTextStripper().getText(document).trim();
			if (content.isBlank()) {
				throw new IllegalArgumentException(
						"PDF 未识别到可检索文本，扫描件请先完成 OCR 后再导入");
			}
			return content;
		}
	}
}

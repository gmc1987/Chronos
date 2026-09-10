package com.chronos.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class KnowledgeDocumentTextExtractorTest {
	private final KnowledgeDocumentTextExtractor extractor = new KnowledgeDocumentTextExtractor();

	@Test
	void extractsSearchablePdfText() throws Exception {
		MockMultipartFile file = pdf("campus-policy.pdf", "Campus safety policy 2026");

		String content = extractor.extract(file, file.getOriginalFilename());

		assertThat(content).contains("Campus safety policy 2026");
	}

	@Test
	void rejectsPdfWithoutSearchableTextWithOcrGuidance() throws Exception {
		MockMultipartFile file = emptyPdf("scanned-policy.pdf");

		assertThatThrownBy(() -> extractor.extract(file, file.getOriginalFilename()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("OCR");
	}

	@Test
	void rejectsUnsupportedDocumentType() {
		MockMultipartFile file = new MockMultipartFile(
				"file",
				"archive.zip",
				"application/zip",
				new byte[] { 1, 2, 3 });

		assertThatThrownBy(() -> extractor.extract(file, file.getOriginalFilename()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("仅支持");
	}

	private MockMultipartFile pdf(String filename, String text) throws Exception {
		try (PDDocument document = new PDDocument();
				ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			PDPage page = new PDPage();
			document.addPage(page);
			try (PDPageContentStream content = new PDPageContentStream(document, page)) {
				content.beginText();
				content.setFont(PDType1Font.HELVETICA, 12);
				content.newLineAtOffset(72, 720);
				content.showText(text);
				content.endText();
			}
			document.save(output);
			return new MockMultipartFile(
					"file",
					filename,
					"application/pdf",
					output.toByteArray());
		}
	}

	private MockMultipartFile emptyPdf(String filename) throws Exception {
		try (PDDocument document = new PDDocument();
				ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			document.addPage(new PDPage());
			document.save(output);
			return new MockMultipartFile(
					"file",
					filename,
					"application/pdf",
					output.toByteArray());
		}
	}
}

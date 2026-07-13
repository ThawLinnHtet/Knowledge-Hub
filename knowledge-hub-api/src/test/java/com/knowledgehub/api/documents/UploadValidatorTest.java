package com.knowledgehub.api.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knowledgehub.api.common.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

class UploadValidatorTest {

	private UploadValidator validator;

	@BeforeEach
	void setUp() {
		validator = new UploadValidator(new UploadProperties(
				Set.of("pdf", "docx", "txt", "md", "markdown"),
				Set.of(
						"application/pdf",
						"application/vnd.openxmlformats-officedocument.wordprocessingml.document",
						"text/plain",
						"text/markdown"),
				1024 * 1024,
				20,
				Duration.ofMinutes(15),
				Duration.ofHours(1)));
	}

	@Test
	void detectsContentAndHashesItWithoutTrustingBrowserMediaType() {
		MockMultipartFile file = new MockMultipartFile(
				"files", "notes.TXT", MediaType.APPLICATION_PDF_VALUE, "trusted notes".getBytes(StandardCharsets.UTF_8));

		UploadValidator.ValidatedUpload upload = validator.validate(file);

		assertThat(upload.filename()).isEqualTo("notes.TXT");
		assertThat(upload.extension()).isEqualTo("txt");
		assertThat(upload.detectedMediaType()).isEqualTo("text/plain");
		assertThat(upload.sha256Hash())
				.isEqualTo("25a96fd99d923b0b834b8fe238fde4bf59b9df583a2be9c74f6e70671e8bca58");
	}

	@Test
	void acceptsPdfDocxAndMarkdownContent() throws Exception {
		assertThat(validator.validate(file("guide.pdf", "%PDF-1.7\nbody")).detectedMediaType())
				.isEqualTo("application/pdf");
		assertThat(validator.validate(new MockMultipartFile(
						"files", "guide.docx", "application/zip", minimalDocx()))
				.detectedMediaType())
				.isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
		assertThat(validator.validate(new MockMultipartFile(
						"files", "large.docx", "application/zip", minimalDocx("x".repeat(70_000))))
				.detectedMediaType())
				.isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
		assertThat(validator.validate(file("README.md", "# Knowledge Hub")).detectedMediaType())
				.isIn("text/plain", "text/markdown");
	}

	@Test
	void rejectsUnsupportedMismatchedEmptyOversizedAndCorruptFiles() throws Exception {
		assertRejected(file("script.exe", "binary"), ErrorCode.VALIDATION_FAILED);
		assertRejected(file("renamed.pdf", "plain text"), ErrorCode.VALIDATION_FAILED);
		assertRejected(
				new MockMultipartFile("files", "renamed.docx", "application/zip", ordinaryZip()),
				ErrorCode.VALIDATION_FAILED);
		assertRejected(file("empty.txt", ""), ErrorCode.VALIDATION_FAILED);
		assertRejected(
				new MockMultipartFile(
						"files", "large.txt", MediaType.TEXT_PLAIN_VALUE, new byte[1024 * 1024 + 1]),
				ErrorCode.LIMIT_EXCEEDED);
	}

	private void assertRejected(MockMultipartFile file, ErrorCode expectedCode) {
		assertThatThrownBy(() -> validator.validate(file))
				.isInstanceOf(UploadValidator.UploadRejectedException.class)
				.satisfies(exception -> assertThat(
							((UploadValidator.UploadRejectedException) exception).code())
						.isEqualTo(expectedCode));
	}

	private MockMultipartFile file(String filename, String content) {
		return new MockMultipartFile(
				"files", filename, null, content.getBytes(StandardCharsets.UTF_8));
	}

	private byte[] minimalDocx() throws IOException {
		return minimalDocx("Knowledge Hub");
	}

	private byte[] minimalDocx(String text) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			entry(
					zip,
					"[Content_Types].xml",
					"<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
							+ "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
							+ "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
							+ "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
							+ "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
							+ "</Types>");
			entry(
					zip,
					"_rels/.rels",
					"<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
							+ "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
							+ "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
							+ "</Relationships>");
			entry(
					zip,
					"word/document.xml",
					"<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
							+ "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
							+ "<w:body><w:p><w:r><w:t>"
							+ text
							+ "</w:t></w:r></w:p></w:body>"
							+ "</w:document>");
		}
		return bytes.toByteArray();
	}

	private byte[] ordinaryZip() throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			entry(zip, "notes.txt", "not a Word document");
		}
		return bytes.toByteArray();
	}

	private void entry(ZipOutputStream zip, String name, String content) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(content.getBytes(StandardCharsets.UTF_8));
		zip.closeEntry();
	}
}

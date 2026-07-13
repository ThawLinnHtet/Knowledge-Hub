package com.knowledgehub.api.documents;

import com.knowledgehub.api.common.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

@Component
@RequiredArgsConstructor
public class UploadValidator {

	private static final Map<String, Set<String>> MIME_TYPES_BY_EXTENSION = Map.of(
			"pdf", Set.of("application/pdf"),
			"docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
			"txt", Set.of("text/plain"),
			"md", Set.of("text/plain", "text/markdown"),
			"markdown", Set.of("text/plain", "text/markdown"));
	private static final int MAX_DOCX_XML_BYTES = 64 * 1024;
	private static final String CONTENT_TYPES_NAMESPACE =
			"http://schemas.openxmlformats.org/package/2006/content-types";
	private static final String RELATIONSHIPS_NAMESPACE =
			"http://schemas.openxmlformats.org/package/2006/relationships";
	private static final String WORD_NAMESPACE =
			"http://schemas.openxmlformats.org/wordprocessingml/2006/main";

	private final UploadProperties properties;
	private final Tika tika = new Tika();

	public ValidatedUpload validate(MultipartFile file) {
		String filename = normalizeFilename(file.getOriginalFilename());
		long size = file.getSize();
		if (size == 0) {
			throw rejected(ErrorCode.VALIDATION_FAILED, "The file is empty.");
		}
		if (size > properties.maxFileSizeBytes()) {
			throw rejected(ErrorCode.LIMIT_EXCEEDED, "The file exceeds the configured maximum size.");
		}

		String extension = extension(filename);
		if (!properties.allowedExtensions().contains(extension)
				|| !MIME_TYPES_BY_EXTENSION.containsKey(extension)) {
			throw rejected(ErrorCode.VALIDATION_FAILED, "The file extension is not supported.");
		}

		try {
			String detectedMediaType;
			try (InputStream input = file.getInputStream()) {
				detectedMediaType = tika.detect(input).toLowerCase(Locale.ROOT);
			}
			if (extension.equals("docx")) {
				detectedMediaType = isDocxPackage(file)
						? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
						: detectedMediaType;
			}
			if ((extension.equals("md") || extension.equals("markdown"))
					&& (detectedMediaType.equals("text/x-web-markdown")
							|| detectedMediaType.equals("text/x-markdown"))) {
				detectedMediaType = "text/markdown";
			}
			if (!properties.allowedMimeTypes().contains(detectedMediaType)
					|| !MIME_TYPES_BY_EXTENSION.get(extension).contains(detectedMediaType)) {
				throw rejected(
						ErrorCode.VALIDATION_FAILED,
						"The detected file content does not match its extension.");
			}
			return new ValidatedUpload(
					filename, extension, detectedMediaType, size, sha256(file));
		} catch (IOException exception) {
			throw rejected(ErrorCode.VALIDATION_FAILED, "The file could not be read.");
		}
	}

	private boolean isDocxPackage(MultipartFile file) throws IOException {
		Path temporaryFile = Files.createTempFile("knowledge-hub-docx-", ".zip");
		try {
			try (InputStream input = file.getInputStream()) {
				Files.copy(input, temporaryFile, StandardCopyOption.REPLACE_EXISTING);
			}
			try (ZipFile zip = new ZipFile(temporaryFile.toFile())) {
				return validContentTypes(zip)
						&& validRootRelationships(zip)
						&& validWordDocument(zip);
			} catch (ZipException exception) {
				return false;
		}
		} finally {
			Files.deleteIfExists(temporaryFile);
		}
	}

	private boolean validContentTypes(ZipFile zip) throws IOException {
		Document document = parseXml(zip, "[Content_Types].xml");
		if (!hasRoot(document, CONTENT_TYPES_NAMESPACE, "Types")) {
			return false;
		}
		var overrides = document.getElementsByTagNameNS(CONTENT_TYPES_NAMESPACE, "Override");
		for (int index = 0; index < overrides.getLength(); index++) {
			Element override = (Element) overrides.item(index);
			if (override.getAttribute("PartName").equals("/word/document.xml")
					&& override
							.getAttribute("ContentType")
							.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml")) {
				return true;
			}
		}
		return false;
	}

	private boolean validRootRelationships(ZipFile zip) throws IOException {
		Document document = parseXml(zip, "_rels/.rels");
		if (!hasRoot(document, RELATIONSHIPS_NAMESPACE, "Relationships")) {
			return false;
		}
		var relationships =
				document.getElementsByTagNameNS(RELATIONSHIPS_NAMESPACE, "Relationship");
		for (int index = 0; index < relationships.getLength(); index++) {
			Element relationship = (Element) relationships.item(index);
			String target = relationship.getAttribute("Target");
			if (relationship.getAttribute("Type").endsWith("/officeDocument")
					&& (target.equals("word/document.xml") || target.equals("/word/document.xml"))) {
				return true;
			}
		}
		return false;
	}

	private boolean validWordDocument(ZipFile zip) throws IOException {
		ZipEntry entry = zip.getEntry("word/document.xml");
		if (entry == null || entry.isDirectory()) {
			return false;
		}
		try (InputStream input = zip.getInputStream(entry)) {
			byte[] prefix = input.readNBytes(MAX_DOCX_XML_BYTES);
			XMLInputFactory factory = XMLInputFactory.newFactory();
			factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
			factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
			var reader = factory.createXMLStreamReader(new ByteArrayInputStream(prefix));
			try {
				while (reader.hasNext()) {
					if (reader.next() == XMLStreamConstants.START_ELEMENT) {
						return WORD_NAMESPACE.equals(reader.getNamespaceURI())
								&& "document".equals(reader.getLocalName());
					}
				}
			} finally {
				reader.close();
			}
		} catch (XMLStreamException exception) {
			return false;
		}
		return false;
	}

	private Document parseXml(ZipFile zip, String entryName) throws IOException {
		ZipEntry entry = zip.getEntry(entryName);
		if (entry == null || entry.isDirectory() || entry.getSize() > MAX_DOCX_XML_BYTES) {
			return null;
		}
		try (InputStream input = zip.getInputStream(entry)) {
			byte[] content = input.readNBytes(MAX_DOCX_XML_BYTES + 1);
			if (content.length > MAX_DOCX_XML_BYTES) {
				return null;
			}
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			return factory.newDocumentBuilder().parse(new ByteArrayInputStream(content));
		} catch (ParserConfigurationException | SAXException exception) {
			return null;
		}
	}

	private boolean hasRoot(Document document, String namespace, String localName) {
		return document != null
				&& document.getDocumentElement() != null
				&& namespace.equals(document.getDocumentElement().getNamespaceURI())
				&& localName.equals(document.getDocumentElement().getLocalName());
	}

	private String normalizeFilename(String originalFilename) {
		if (originalFilename == null || originalFilename.isBlank()) {
			throw rejected(ErrorCode.VALIDATION_FAILED, "A filename is required.");
		}
		String normalized = originalFilename.replace('\\', '/');
		normalized = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
		if (normalized.isBlank() || normalized.length() > 512) {
			throw rejected(ErrorCode.VALIDATION_FAILED, "The filename is invalid.");
		}
		if (normalized.codePoints().anyMatch(Character::isISOControl)) {
			throw rejected(ErrorCode.VALIDATION_FAILED, "The filename contains control characters.");
		}
		return normalized;
	}

	private String extension(String filename) {
		int separator = filename.lastIndexOf('.');
		if (separator < 1 || separator == filename.length() - 1) {
			throw rejected(ErrorCode.VALIDATION_FAILED, "The file extension is not supported.");
		}
		return filename.substring(separator + 1).toLowerCase(Locale.ROOT);
	}

	private String sha256(MultipartFile file) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (DigestInputStream input = new DigestInputStream(file.getInputStream(), digest)) {
				input.transferTo(java.io.OutputStream.nullOutputStream());
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private UploadRejectedException rejected(ErrorCode code, String message) {
		return new UploadRejectedException(code, message);
	}

	public record ValidatedUpload(
			String filename, String extension, String detectedMediaType, long sizeBytes, String sha256Hash) {}

	public static final class UploadRejectedException extends RuntimeException {

		private final ErrorCode code;

		private UploadRejectedException(ErrorCode code, String message) {
			super(message);
			this.code = code;
		}

		public ErrorCode code() {
			return code;
		}
	}
}

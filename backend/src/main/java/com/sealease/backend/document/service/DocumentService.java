package com.sealease.backend.document.service;

import com.sealease.backend.common.crypto.FieldEncryptor;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.document.entity.DocumentPurpose;
import com.sealease.backend.document.entity.StoredDocument;
import com.sealease.backend.document.repository.StoredDocumentRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Encrypted document storage. File type is decided from the file's magic bytes, never from the
 * client-supplied name or Content-Type, and only PDF, JPEG and PNG are accepted. Each file is
 * encrypted with a context bound to its owner and purpose, and its SHA-256 is checked on read.
 */
@Service
@EnableConfigurationProperties(DocumentProperties.class)
public class DocumentService {

	private static final byte[] PDF = { '%', 'P', 'D', 'F', '-' };
	private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };

	private final StoredDocumentRepository documents;
	private final FieldEncryptor encryptor;
	private final DocumentProperties properties;
	private final Clock clock;

	public DocumentService(StoredDocumentRepository documents, FieldEncryptor encryptor, DocumentProperties properties,
			Clock clock) {
		this.documents = documents;
		this.encryptor = encryptor;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public UUID store(UUID ownerUserId, DocumentPurpose purpose, MultipartFile file) {
		byte[] bytes = read(file);
		return store(ownerUserId, purpose, bytes);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public UUID store(UUID ownerUserId, DocumentPurpose purpose, byte[] bytes) {
		if (bytes.length == 0) {
			throw invalid(purpose, "is empty");
		}
		if (bytes.length > properties.maxFileSize().toBytes()) {
			throw invalid(purpose, "exceeds the maximum size of " + properties.maxFileSize().toMegabytes() + " MB");
		}
		String contentType = detectContentType(bytes);
		if (contentType == null) {
			throw invalid(purpose, "must be a PDF, JPEG or PNG file");
		}
		byte[] encrypted = encryptor.encryptBytes(bytes, context(ownerUserId, purpose));
		// Flushed so rows referencing it by id (e.g. kyc_documents) can be inserted safely.
		StoredDocument saved = documents.saveAndFlush(new StoredDocument(ownerUserId, purpose, contentType, bytes.length,
				sha256(bytes), encrypted, clock.instant()));
		return saved.getId();
	}

	@Transactional(readOnly = true)
	public DocumentContent load(UUID documentId) {
		StoredDocument document = documents.findById(documentId)
			.orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
		byte[] bytes = encryptor.decryptBytes(document.getContent(),
				context(document.getOwnerUserId(), document.getPurpose()));
		if (!sha256(bytes).equals(document.getSha256())) {
			throw new IllegalStateException("Integrity check failed for document " + documentId);
		}
		return new DocumentContent(document.getId(), document.getOwnerUserId(), document.getPurpose(),
				document.getContentType(), bytes);
	}

	public static String detectContentType(byte[] bytes) {
		if (startsWith(bytes, PDF)) {
			return "application/pdf";
		}
		if (startsWith(bytes, JPEG)) {
			return "image/jpeg";
		}
		if (startsWith(bytes, PNG)) {
			return "image/png";
		}
		return null;
	}

	private static boolean startsWith(byte[] bytes, byte[] prefix) {
		return bytes.length >= prefix.length && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
	}

	private static String context(UUID ownerUserId, DocumentPurpose purpose) {
		return "stored_document|" + ownerUserId + "|" + purpose;
	}

	private byte[] read(MultipartFile file) {
		if (file.getSize() > properties.maxFileSize().toBytes()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"File exceeds the maximum size of " + properties.maxFileSize().toMegabytes() + " MB");
		}
		try {
			return file.getBytes();
		}
		catch (IOException ex) {
			throw new BusinessException(ErrorCode.MALFORMED_REQUEST, "Uploaded file could not be read", ex);
		}
	}

	private static BusinessException invalid(DocumentPurpose purpose, String problem) {
		return new BusinessException(ErrorCode.VALIDATION_FAILED, "Document " + purpose + " " + problem);
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

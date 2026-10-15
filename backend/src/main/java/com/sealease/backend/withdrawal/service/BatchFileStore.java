package com.sealease.backend.withdrawal.service;

import com.sealease.backend.common.crypto.FieldEncryptor;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.withdrawal.dto.BatchFileResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The files kept with payout batches ({@code withdrawal_batch_files}): the generated bank file and the
 * reconciliation files uploaded back. Content is encrypted, being full bank account details. Excel files are kept
 * Base64-encoded; CSV files from before them as text.
 */
@Component
class BatchFileStore {

	static final String PAYMENT = "PAYMENT";
	private static final String CONTEXT = "withdrawal_batch_file.content";

	private static final RowMapper<BatchFileResponse> FILE = (rs, i) -> new BatchFileResponse(
			rs.getObject("id", UUID.class), rs.getString("kind"), rs.getString("filename"), rs.getInt("size_bytes"),
			rs.getObject("uploaded_by", UUID.class), (Integer) rs.getObject("paid_count"),
			(Integer) rs.getObject("failed_count"), rs.getTimestamp("created_at").toInstant());

	private final JdbcTemplate jdbc;
	private final FieldEncryptor encryptor;
	private final Clock clock;

	BatchFileStore(JdbcTemplate jdbc, FieldEncryptor encryptor, Clock clock) {
		this.jdbc = jdbc;
		this.encryptor = encryptor;
		this.clock = clock;
	}

	/** Keeps a file with a batch, in the caller's transaction. */
	@Transactional(propagation = Propagation.MANDATORY)
	UUID save(UUID batchId, String kind, String filename, byte[] content, UUID uploadedBy, Integer paid, Integer failed) {
		UUID id = UUID.randomUUID();
		String stored = isText(filename) ? new String(content, StandardCharsets.UTF_8) : Base64.getEncoder().encodeToString(content);
		jdbc.update("""
				INSERT INTO withdrawal_batch_files (id, batch_id, kind, filename, content_encrypted, size_bytes, uploaded_by,
				                                    paid_count, failed_count, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", id, batchId, kind, filename, encryptor.encrypt(stored, CONTEXT), content.length, uploadedBy, paid, failed,
				Timestamp.from(clock.instant()));
		return id;
	}

	@Transactional(readOnly = true)
	List<BatchFileResponse> list(UUID batchId) {
		return jdbc.query("""
				SELECT id, kind, filename, size_bytes, uploaded_by, paid_count, failed_count, created_at
				FROM withdrawal_batch_files WHERE batch_id = ? ORDER BY created_at, id
				""", FILE, batchId);
	}

	/** The batch's bank file, if it was kept. */
	@Transactional(readOnly = true)
	Optional<StoredFile> payment(UUID batchId) {
		return jdbc.query("SELECT kind, filename, content_encrypted FROM withdrawal_batch_files WHERE batch_id = ? AND kind = ?",
				(rs, i) -> stored(rs.getString("kind"), rs.getString("filename"), rs.getString("content_encrypted")), batchId,
				PAYMENT).stream().findFirst();
	}

	@Transactional(readOnly = true)
	StoredFile content(UUID batchId, UUID fileId) {
		return jdbc.query("SELECT kind, filename, content_encrypted FROM withdrawal_batch_files WHERE batch_id = ? AND id = ?",
				(rs, i) -> stored(rs.getString("kind"), rs.getString("filename"), rs.getString("content_encrypted")), batchId,
				fileId).stream().findFirst()
			.orElseThrow(() -> new ResourceNotFoundException("Batch file", fileId));
	}

	private StoredFile stored(String kind, String filename, String encrypted) {
		String content = encryptor.decrypt(encrypted, CONTEXT);
		return new StoredFile(kind, filename, isText(filename) ? content.getBytes(StandardCharsets.UTF_8) : Base64.getDecoder().decode(content));
	}

	private static boolean isText(String filename) {
		return filename.toLowerCase(java.util.Locale.ROOT).endsWith(".csv");
	}

	/** @param kind PAYMENT (the sheet made with the batch); RECONCILIATION for completed sheets uploaded before uploads were removed */
	record StoredFile(String kind, String filename, byte[] content) {

		boolean isPayment() {
			return PAYMENT.equals(kind);
		}

	}

}

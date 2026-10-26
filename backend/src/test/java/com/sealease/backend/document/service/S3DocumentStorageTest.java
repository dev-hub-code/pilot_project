package com.sealease.backend.document.service;

import com.sealease.backend.common.crypto.CryptoProperties;
import com.sealease.backend.common.crypto.FieldEncryptor;
import com.sealease.backend.document.entity.DocumentPurpose;
import com.sealease.backend.document.entity.StoredDocument;
import com.sealease.backend.document.repository.StoredDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** With a bucket configured, documents' encrypted bytes go to S3 and the row keeps only the object key. */
class S3DocumentStorageTest {

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3 };

	/** The bucket, in memory. */
	private final Map<String, byte[]> bucket = new HashMap<>();
	private final S3DocumentStore store = new S3DocumentStore(null, "documents") {
		@Override
		void put(String key, byte[] encrypted) {
			bucket.put(key, encrypted);
		}

		@Override
		byte[] get(String key) {
			return bucket.get(key);
		}
	};

	private final StoredDocumentRepository rows = mock(StoredDocumentRepository.class);
	private final FieldEncryptor encryptor = new FieldEncryptor(new CryptoProperties("a:" + key(), "a", key()));
	private final DocumentService documents = new DocumentService(rows, encryptor,
			new DocumentProperties(DataSize.ofMegabytes(5), new DocumentProperties.S3("documents", "ap-southeast-2")),
			Clock.systemUTC(), store);

	@Test
	void storesTheEncryptedBytesInTheBucketAndReadsThemBack() {
		Map<UUID, StoredDocument> saved = new HashMap<>();
		when(rows.saveAndFlush(any())).thenAnswer(call -> {
			StoredDocument row = call.getArgument(0);
			ReflectionTestUtils.setField(row, "id", UUID.randomUUID());
			saved.put(row.getId(), row);
			return row;
		});
		when(rows.findById(any())).thenAnswer(call -> Optional.ofNullable(saved.get(call.<UUID>getArgument(0))));
		UUID owner = UUID.randomUUID();

		UUID id = documents.store(owner, DocumentPurpose.KYC_SELFIE, PNG);

		StoredDocument row = saved.get(id);
		assertThat(row.getContent()).as("no bytes in the database").isNull();
		assertThat(row.getStorageKey()).startsWith("kyc_selfie/");
		assertThat(bucket).containsOnlyKeys(row.getStorageKey());
		assertThat(bucket.get(row.getStorageKey())).as("encrypted before upload").isNotEqualTo(PNG);
		DocumentContent loaded = documents.load(id);
		assertThat(loaded.bytes()).isEqualTo(PNG);
		assertThat(loaded.contentType()).isEqualTo("image/png");
		assertThat(loaded.ownerUserId()).isEqualTo(owner);
	}

	private static String key() {
		byte[] key = new byte[32];
		new SecureRandom().nextBytes(key);
		return Base64.getEncoder().encodeToString(key);
	}

}

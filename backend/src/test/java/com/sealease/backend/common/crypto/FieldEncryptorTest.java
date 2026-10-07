package com.sealease.backend.common.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldEncryptorTest {

	private static final String KEY_A = randomKey();
	private static final String KEY_B = randomKey();
	private static final String FINGERPRINT_KEY = randomKey();

	private final FieldEncryptor encryptor = new FieldEncryptor(
			new CryptoProperties("a:" + KEY_A, "a", FINGERPRINT_KEY));

	@Test
	void roundTripsTextWithoutLeakingPlaintext() {
		String encrypted = encryptor.encrypt("GB82WEST12345698765432", "bank_account.account_number");

		assertThat(encrypted).startsWith("a:").doesNotContain("12345698765432");
		assertThat(encryptor.decrypt(encrypted, "bank_account.account_number")).isEqualTo("GB82WEST12345698765432");
	}

	@Test
	void sameValueEncryptsDifferentlyEachTime() {
		assertThat(encryptor.encrypt("ABC123", "ctx")).isNotEqualTo(encryptor.encrypt("ABC123", "ctx"));
	}

	@Test
	void ciphertextIsBoundToItsContext() {
		String taxId = encryptor.encrypt("123-45-6789", "user_profile.tax_id");
		assertThatThrownBy(() -> encryptor.decrypt(taxId, "bank_account.account_number"))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void tamperingIsDetected() {
		String encrypted = encryptor.encrypt("secret", "ctx");
		byte[] raw = Base64.getDecoder().decode(encrypted.substring(2));
		raw[raw.length - 1] ^= 1;
		String tampered = "a:" + Base64.getEncoder().encodeToString(raw);
		assertThatThrownBy(() -> encryptor.decrypt(tampered, "ctx")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void rotationKeepsOldCiphertextReadable() {
		String underOldKey = encryptor.encrypt("legacy", "ctx");
		FieldEncryptor rotated = new FieldEncryptor(new CryptoProperties("a:" + KEY_A + ",b:" + KEY_B, "b",
				FINGERPRINT_KEY));

		assertThat(rotated.decrypt(underOldKey, "ctx")).isEqualTo("legacy");
		assertThat(rotated.encrypt("fresh", "ctx")).startsWith("b:");
	}

	@Test
	void bytesRoundTrip() {
		byte[] file = "%PDF-1.7 confidential passport scan".getBytes(StandardCharsets.UTF_8);
		byte[] encrypted = encryptor.encryptBytes(file, "doc|owner|purpose");

		assertThat(new String(encrypted, StandardCharsets.ISO_8859_1)).doesNotContain("passport");
		assertThat(encryptor.decryptBytes(encrypted, "doc|owner|purpose")).isEqualTo(file);
		assertThatThrownBy(() -> encryptor.decryptBytes(encrypted, "doc|other-owner|purpose"))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void fingerprintsAreDeterministicAndContextSeparated() {
		String one = encryptor.fingerprint("SWIFT|12345678", "bank");
		assertThat(encryptor.fingerprint("SWIFT|12345678", "bank")).isEqualTo(one).hasSize(64);
		assertThat(encryptor.fingerprint("SWIFT|12345678", "other")).isNotEqualTo(one);
		assertThat(encryptor.fingerprint("SWIFT|12345679", "bank")).isNotEqualTo(one);
	}

	@Test
	void rejectsMisconfiguration() {
		assertThatThrownBy(() -> new FieldEncryptor(new CryptoProperties(null, "a", FINGERPRINT_KEY)))
			.hasMessageContaining("data-keys");
		assertThatThrownBy(() -> new FieldEncryptor(new CryptoProperties("a:" + KEY_A, "missing", FINGERPRINT_KEY)))
			.hasMessageContaining("active-key-id");
		String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
		assertThatThrownBy(() -> new FieldEncryptor(new CryptoProperties("a:" + shortKey, "a", FINGERPRINT_KEY)))
			.hasMessageContaining("256-bit");
	}

	private static String randomKey() {
		byte[] key = new byte[32];
		new SecureRandom().nextBytes(key);
		return Base64.getEncoder().encodeToString(key);
	}

}

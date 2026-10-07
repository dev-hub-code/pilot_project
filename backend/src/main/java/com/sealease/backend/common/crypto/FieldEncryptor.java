package com.sealease.backend.common.crypto;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

/**
 * AES-256-GCM encryption for sensitive fields and files, plus HMAC fingerprints.
 *
 * <p>Every ciphertext is bound to a <em>context</em> string (e.g. {@code "bank_account.number"})
 * as GCM associated data, so a ciphertext copied into a different column fails to decrypt instead
 * of revealing data in the wrong place. Ciphertexts carry their key id, enabling key rotation.
 *
 * <p>Text format: {@code <keyId>:<base64(iv || ciphertext+tag)>}. Binary format:
 * {@code [keyIdLength:1][keyId][iv:12][ciphertext+tag]}.
 */
@Component
@EnableConfigurationProperties(CryptoProperties.class)
public class FieldEncryptor {

	private static final String CIPHER = "AES/GCM/NoPadding";
	private static final int IV_BYTES = 12;
	private static final int TAG_BITS = 128;
	private static final int KEY_BYTES = 32;

	private final SecureRandom random = new SecureRandom();
	private final Map<String, SecretKey> keys;
	private final String activeKeyId;
	private final SecretKey fingerprintKey;

	public FieldEncryptor(CryptoProperties properties) {
		this.keys = parseKeys(require(properties.dataKeys(), "app.crypto.data-keys"));
		this.activeKeyId = require(properties.activeKeyId(), "app.crypto.active-key-id");
		if (!keys.containsKey(activeKeyId)) {
			throw new IllegalStateException("app.crypto.active-key-id '" + activeKeyId + "' is not in app.crypto.data-keys");
		}
		byte[] hmacKey = decodeKey(require(properties.fingerprintKey(), "app.crypto.fingerprint-key"));
		this.fingerprintKey = new SecretKeySpec(hmacKey, "HmacSHA256");
	}

	public String encrypt(String plaintext, String context) {
		Objects.requireNonNull(plaintext, "plaintext");
		byte[] ivAndCiphertext = seal(plaintext.getBytes(StandardCharsets.UTF_8), context, activeKeyId);
		return activeKeyId + ":" + Base64.getEncoder().encodeToString(ivAndCiphertext);
	}

	public String decrypt(String encrypted, String context) {
		int separator = encrypted.indexOf(':');
		if (separator < 1) {
			throw new IllegalArgumentException("Malformed ciphertext");
		}
		String keyId = encrypted.substring(0, separator);
		byte[] ivAndCiphertext = Base64.getDecoder().decode(encrypted.substring(separator + 1));
		return new String(open(ivAndCiphertext, context, keyId), StandardCharsets.UTF_8);
	}

	public byte[] encryptBytes(byte[] plaintext, String context) {
		byte[] keyId = activeKeyId.getBytes(StandardCharsets.US_ASCII);
		byte[] sealed = seal(plaintext, context, activeKeyId);
		return ByteBuffer.allocate(1 + keyId.length + sealed.length)
			.put((byte) keyId.length)
			.put(keyId)
			.put(sealed)
			.array();
	}

	public byte[] decryptBytes(byte[] encrypted, String context) {
		ByteBuffer buffer = ByteBuffer.wrap(encrypted);
		byte[] keyId = new byte[Byte.toUnsignedInt(buffer.get())];
		buffer.get(keyId);
		byte[] sealed = new byte[buffer.remaining()];
		buffer.get(sealed);
		return open(sealed, context, new String(keyId, StandardCharsets.US_ASCII));
	}

	/** Deterministic keyed fingerprint: equal inputs give equal outputs, but cannot be reversed. */
	public String fingerprint(String value, String context) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(fingerprintKey);
			mac.update(context.getBytes(StandardCharsets.UTF_8));
			mac.update((byte) 0);
			return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Fingerprint failed", ex);
		}
	}

	/** Last {@code n} characters, for masked display ("•••• 6789"). */
	public static String lastChars(String value, int n) {
		return value.length() <= n ? value : value.substring(value.length() - n);
	}

	private byte[] seal(byte[] plaintext, String context, String keyId) {
		try {
			byte[] iv = new byte[IV_BYTES];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance(CIPHER);
			cipher.init(Cipher.ENCRYPT_MODE, keys.get(keyId), new GCMParameterSpec(TAG_BITS, iv));
			cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
			byte[] ciphertext = cipher.doFinal(plaintext);
			return ByteBuffer.allocate(IV_BYTES + ciphertext.length).put(iv).put(ciphertext).array();
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Encryption failed", ex);
		}
	}

	private byte[] open(byte[] ivAndCiphertext, String context, String keyId) {
		SecretKey key = keys.get(keyId);
		if (key == null) {
			throw new IllegalStateException("Unknown encryption key id: " + keyId);
		}
		try {
			Cipher cipher = Cipher.getInstance(CIPHER);
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, ivAndCiphertext, 0, IV_BYTES));
			cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
			return cipher.doFinal(ivAndCiphertext, IV_BYTES, ivAndCiphertext.length - IV_BYTES);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Decryption failed (wrong key or context, or data tampered)", ex);
		}
	}

	private static Map<String, SecretKey> parseKeys(String spec) {
		Map<String, SecretKey> parsed = new HashMap<>();
		for (String entry : spec.split(",")) {
			String[] parts = entry.strip().split(":", 2);
			if (parts.length != 2 || parts[0].isBlank() || parts[0].length() > 32) {
				throw new IllegalStateException("app.crypto.data-keys entries must be keyId:base64Key");
			}
			parsed.put(parts[0], new SecretKeySpec(decodeKey(parts[1]), "AES"));
		}
		return Map.copyOf(parsed);
	}

	private static byte[] decodeKey(String base64) {
		byte[] key = Base64.getDecoder().decode(base64.strip());
		if (key.length != KEY_BYTES) {
			throw new IllegalStateException("Encryption keys must be 256-bit (32 bytes, base64-encoded)");
		}
		return key;
	}

	private static String require(String value, String property) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(property + " must be configured");
		}
		return value;
	}

}

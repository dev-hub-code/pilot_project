package com.sealease.backend.common.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Field-level encryption keys. Supplied from the environment / secret manager; never committed.
 *
 * @param dataKeys       comma-separated {@code keyId:base64Key} entries (256-bit AES keys). Old keys
 *                       stay listed after rotation so existing ciphertext remains readable.
 * @param activeKeyId    key used for new encryptions
 * @param fingerprintKey base64 HMAC-SHA256 key for deterministic fingerprints (duplicate detection)
 */
@ConfigurationProperties(prefix = "app.crypto")
public record CryptoProperties(String dataKeys, String activeKeyId, String fingerprintKey) {

	@Override
	public String toString() {
		return "CryptoProperties[activeKeyId=" + activeKeyId + "]";
	}

}

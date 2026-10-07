package com.sealease.backend.auth.jwt;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.regex.Pattern;

/** Loads and sanity-checks the PEM-encoded RSA key pair used for RS256 tokens. */
public final class RsaKeyLoader {

	private static final int MIN_KEY_BITS = 2048;
	private static final Pattern PEM_ARMOUR = Pattern.compile("-----(BEGIN|END) [A-Z ]+-----|\\s");

	private RsaKeyLoader() {
	}

	public static RsaKeyPair load(Resource privateKeyPem, Resource publicKeyPem) {
		if (privateKeyPem == null || publicKeyPem == null) {
			throw new IllegalStateException(
					"JWT signing keys are not configured: set app.auth.jwt.private-key and app.auth.jwt.public-key");
		}
		try {
			KeyFactory factory = KeyFactory.getInstance("RSA");
			RSAPrivateKey privateKey = (RSAPrivateKey) factory
				.generatePrivate(new PKCS8EncodedKeySpec(decode(privateKeyPem, "PRIVATE KEY")));
			RSAPublicKey publicKey = (RSAPublicKey) factory
				.generatePublic(new X509EncodedKeySpec(decode(publicKeyPem, "PUBLIC KEY")));
			RsaKeyPair pair = new RsaKeyPair(publicKey, privateKey);
			verify(pair);
			return pair;
		}
		catch (IOException | GeneralSecurityException | IllegalArgumentException ex) {
			throw new IllegalStateException("Unable to load JWT RSA keys: " + ex.getMessage(), ex);
		}
	}

	private static byte[] decode(Resource resource, String expectedType) throws IOException {
		String pem;
		try (InputStream in = resource.getInputStream()) {
			pem = new String(in.readAllBytes(), StandardCharsets.US_ASCII);
		}
		if (!pem.contains("-----BEGIN " + expectedType + "-----")) {
			throw new IllegalArgumentException(resource.getDescription() + " is not a PEM '" + expectedType
					+ "' (convert RSA keys to PKCS#8 / X.509 SubjectPublicKeyInfo)");
		}
		return Base64.getDecoder().decode(PEM_ARMOUR.matcher(pem).replaceAll(""));
	}

	private static void verify(RsaKeyPair pair) throws GeneralSecurityException {
		if (pair.publicKey().getModulus().bitLength() < MIN_KEY_BITS) {
			throw new IllegalArgumentException("RSA keys must be at least " + MIN_KEY_BITS + " bits");
		}
		byte[] probe = "sealease-key-pair-check".getBytes(StandardCharsets.US_ASCII);
		Signature signer = Signature.getInstance("SHA256withRSA");
		signer.initSign(pair.privateKey());
		signer.update(probe);
		byte[] signature = signer.sign();
		Signature verifier = Signature.getInstance("SHA256withRSA");
		verifier.initVerify(pair.publicKey());
		verifier.update(probe);
		if (!verifier.verify(signature)) {
			throw new IllegalArgumentException("Public key does not match private key");
		}
	}

	public record RsaKeyPair(RSAPublicKey publicKey, RSAPrivateKey privateKey) {

		@Override
		public String toString() {
			return "RsaKeyPair[bits=" + publicKey.getModulus().bitLength() + "]";
		}

	}

}

package com.sealease.backend.auth.controller;

import com.nimbusds.jose.jwk.JWKSet;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/** Publishes the token verification key (public only) in standard JWK Set format. */
@RestController
public class JwksController {

	private final JWKSet publicJwkSet;

	public JwksController(JWKSet publicJwkSet) {
		this.publicJwkSet = publicJwkSet;
	}

	@GetMapping("/api/v1/auth/jwks")
	public ResponseEntity<Map<String, Object>> jwks() {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
			.body(publicJwkSet.toJSONObject(true));
	}

}

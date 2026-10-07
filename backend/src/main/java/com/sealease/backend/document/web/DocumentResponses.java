package com.sealease.backend.document.web;

import com.sealease.backend.document.service.DocumentContent;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Serves a decrypted document inline: never cached, never sniffed, and sandboxed so that a crafted
 * file cannot execute script in the application's origin.
 */
public final class DocumentResponses {

	private DocumentResponses() {
	}

	public static ResponseEntity<byte[]> inline(DocumentContent content) {
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(content.contentType()))
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
				.filename(content.purpose().name().toLowerCase() + "." + content.fileExtension())
				.build()
				.toString())
			.header("X-Content-Type-Options", "nosniff")
			.header("Content-Security-Policy", "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; sandbox")
			.cacheControl(CacheControl.noStore())
			.body(content.bytes());
	}

}

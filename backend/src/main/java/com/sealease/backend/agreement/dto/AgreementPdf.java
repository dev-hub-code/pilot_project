package com.sealease.backend.agreement.dto;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** A rendered agreement, downloaded as an attachment and never cached. */
public record AgreementPdf(String filename, byte[] content) {

	public ResponseEntity<byte[]> toResponse() {
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_PDF)
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(filename + ".pdf").build().toString())
			.cacheControl(CacheControl.noStore())
			.body(content);
	}

}

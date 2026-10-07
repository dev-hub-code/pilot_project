package com.sealease.backend.document.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentTypeDetectionTest {

	@Test
	void detectsAllowedTypesByMagicBytes() {
		assertThat(DocumentService.detectContentType("%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII)))
			.isEqualTo("application/pdf");
		assertThat(DocumentService.detectContentType(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00 }))
			.isEqualTo("image/jpeg");
		assertThat(DocumentService.detectContentType(
				new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0x00 }))
			.isEqualTo("image/png");
	}

	@Test
	void rejectsEverythingElseRegardlessOfName() {
		assertThat(DocumentService.detectContentType("<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)))
			.isNull();
		assertThat(DocumentService.detectContentType("<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8))).isNull();
		assertThat(DocumentService.detectContentType(new byte[] { 'P', 'K', 3, 4 })).isNull();
		assertThat(DocumentService.detectContentType(new byte[] { (byte) 0x89, 'P' })).isNull();
	}

}

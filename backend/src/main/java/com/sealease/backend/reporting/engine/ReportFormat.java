package com.sealease.backend.reporting.engine;

import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;

import java.util.Locale;

public enum ReportFormat {

	PDF(MediaType.APPLICATION_PDF, "pdf"),
	CSV(new MediaType("text", "csv", StandardCharsets.UTF_8), "csv"),
	/** The document as data, for on-screen previews. */
	JSON(MediaType.APPLICATION_JSON, "json");

	private final MediaType mediaType;
	private final String extension;

	ReportFormat(MediaType mediaType, String extension) {
		this.mediaType = mediaType;
		this.extension = extension;
	}

	public MediaType mediaType() {
		return mediaType;
	}

	public String extension() {
		return extension;
	}

	public static ReportFormat parse(String value) {
		if (value == null || value.isBlank()) {
			return JSON;
		}
		try {
			return valueOf(value.strip().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException unknown) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "format must be pdf, csv or json");
		}
	}

}

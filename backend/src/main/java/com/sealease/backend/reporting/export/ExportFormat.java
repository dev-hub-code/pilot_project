package com.sealease.backend.reporting.export;

import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public enum ExportFormat {

	CSV(new MediaType("text", "csv", StandardCharsets.UTF_8), "csv"),
	XLSX(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), "xlsx"),
	PDF(MediaType.APPLICATION_PDF, "pdf");

	private final MediaType mediaType;
	private final String extension;

	ExportFormat(MediaType mediaType, String extension) {
		this.mediaType = mediaType;
		this.extension = extension;
	}

	public MediaType mediaType() {
		return mediaType;
	}

	public String extension() {
		return extension;
	}

	public static ExportFormat parse(String value) {
		try {
			return valueOf(value == null ? "" : value.strip().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException unknown) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "format must be csv, xlsx or pdf");
		}
	}

}

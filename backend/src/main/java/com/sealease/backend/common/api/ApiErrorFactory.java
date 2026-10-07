package com.sealease.backend.common.api;

import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.web.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Builds {@link ApiError} bodies, and writes them directly to the servlet response for failures
 * that happen outside Spring MVC (the security filter chain).
 */
@Component
public class ApiErrorFactory {

	private final Clock clock;
	private final JsonMapper jsonMapper;

	public ApiErrorFactory(Clock clock, JsonMapper jsonMapper) {
		this.clock = clock;
		this.jsonMapper = jsonMapper;
	}

	public ApiError create(ErrorCode code, String message, HttpServletRequest request) {
		return create(code, message, request, List.of());
	}

	public ApiError create(ErrorCode code, String message, HttpServletRequest request,
			List<ApiError.FieldError> fieldErrors) {
		return new ApiError(Instant.now(clock), code.status().value(), code.name(), message,
				request.getRequestURI(), CorrelationId.current().orElse(null), fieldErrors);
	}

	public void write(ErrorCode code, String message, HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		ApiError body = create(code, message, request);
		response.setStatus(body.status());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		jsonMapper.writeValue(response.getOutputStream(), body);
	}

}

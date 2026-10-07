package com.sealease.backend.common.exception;

import com.sealease.backend.common.api.ApiError;
import com.sealease.backend.common.api.ApiErrorFactory;
import com.sealease.backend.common.ratelimit.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Translates every exception escaping a controller into the standard {@link ApiError} shape.
 *
 * <p>Only {@link BusinessException} messages reach the client verbatim. Framework and
 * infrastructure exceptions get a fixed, generic message so that SQL, class names, or stack
 * details never leak.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private final ApiErrorFactory errors;

	public GlobalExceptionHandler(ApiErrorFactory errors) {
		this.errors = errors;
	}

	@ExceptionHandler(BusinessException.class)
	ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest request) {
		log.info("Business exception [{}]: {}", ex.errorCode(), ex.getMessage());
		return respond(ex.errorCode(), ex.getMessage(), request);
	}

	@ExceptionHandler(RateLimitExceededException.class)
	ResponseEntity<ApiError> handleRateLimit(RateLimitExceededException ex, HttpServletRequest request) {
		long retryAfterSeconds = Math.max(1, ex.retryAfter().toSeconds());
		return ResponseEntity.status(ex.errorCode().status())
			.header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds))
			.body(errors.create(ex.errorCode(), ex.getMessage(), request));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> handleBodyValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
		List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
			.map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
			.toList();
		return respond(ErrorCode.VALIDATION_FAILED, "Request validation failed", request, fieldErrors);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
		List<ApiError.FieldError> fieldErrors = ex.getParameterValidationResults().stream()
			.flatMap(result -> result.getResolvableErrors().stream()
				.map(error -> new ApiError.FieldError(result.getMethodParameter().getParameterName(),
						error.getDefaultMessage())))
			.toList();
		return respond(ErrorCode.VALIDATION_FAILED, "Request validation failed", request, fieldErrors);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
		List<ApiError.FieldError> fieldErrors = ex.getConstraintViolations().stream()
			.map(v -> new ApiError.FieldError(v.getPropertyPath().toString(), v.getMessage()))
			.toList();
		return respond(ErrorCode.VALIDATION_FAILED, "Request validation failed", request, fieldErrors);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
		return respond(ErrorCode.MALFORMED_REQUEST, "Request body is missing or malformed", request);
	}

	@ExceptionHandler({ MissingServletRequestParameterException.class, MissingRequestHeaderException.class,
			MethodArgumentTypeMismatchException.class })
	ResponseEntity<ApiError> handleBadParameter(Exception ex, HttpServletRequest request) {
		String message = switch (ex) {
			case MissingServletRequestParameterException e -> "Missing request parameter: " + e.getParameterName();
			case MissingRequestHeaderException e -> "Missing request header: " + e.getHeaderName();
			case MethodArgumentTypeMismatchException e -> "Invalid value for parameter: " + e.getName();
			default -> "Invalid request";
		};
		return respond(ErrorCode.INVALID_REQUEST, message, request);
	}

	/** Unknown property in a client-supplied {@code sort} parameter. */
	@ExceptionHandler(PropertyReferenceException.class)
	ResponseEntity<ApiError> handleBadSort(PropertyReferenceException ex, HttpServletRequest request) {
		return respond(ErrorCode.INVALID_REQUEST, "Unsupported sort property: " + ex.getPropertyName(), request);
	}

	/** Spring Data may wrap the sort error during repository exception translation. */
	@ExceptionHandler(InvalidDataAccessApiUsageException.class)
	ResponseEntity<ApiError> handleDataAccessUsage(InvalidDataAccessApiUsageException ex, HttpServletRequest request) {
		if (ex.getMostSpecificCause() instanceof PropertyReferenceException bad) {
			return handleBadSort(bad, request);
		}
		return handleUnexpected(ex, request);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
		return respond(ErrorCode.NOT_FOUND, "Resource not found", request);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
		return respond(ErrorCode.METHOD_NOT_ALLOWED, "HTTP method not supported: " + ex.getMethod(), request);
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	ResponseEntity<ApiError> handleMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
		return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Content type not supported", request);
	}

	@ExceptionHandler({ OptimisticLockingFailureException.class, PessimisticLockingFailureException.class })
	ResponseEntity<ApiError> handleConcurrency(Exception ex, HttpServletRequest request) {
		log.warn("Concurrent modification detected: {}", ex.getMessage());
		return respond(ErrorCode.CONCURRENT_MODIFICATION,
				"The resource was modified concurrently; reload and retry", request);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
		// Constraint names may be mapped to friendlier messages by the owning module; the raw
		// database message is logged only.
		log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
		return respond(ErrorCode.CONFLICT, "Request conflicts with existing data", request);
	}

	/**
	 * Raised by method security ({@code @PreAuthorize}) inside the MVC layer. Must be handled here,
	 * otherwise the catch-all below would turn authorization failures into 500s.
	 */
	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
		return respond(ErrorCode.FORBIDDEN, "Access denied", request);
	}

	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
		return respond(ErrorCode.UNAUTHORIZED, "Authentication required", request);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
		log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
		return respond(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", request);
	}

	private ResponseEntity<ApiError> respond(ErrorCode code, String message, HttpServletRequest request) {
		return respond(code, message, request, List.of());
	}

	private ResponseEntity<ApiError> respond(ErrorCode code, String message, HttpServletRequest request,
			List<ApiError.FieldError> fieldErrors) {
		return ResponseEntity.status(code.status()).body(errors.create(code, message, request, fieldErrors));
	}

}

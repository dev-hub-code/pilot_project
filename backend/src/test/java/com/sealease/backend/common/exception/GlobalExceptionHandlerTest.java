package com.sealease.backend.common.exception;

import com.sealease.backend.common.web.CorrelationId;
import com.sealease.backend.support.WebSliceTestConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.ProbeController.class)
@Import({ WebSliceTestConfig.class, GlobalExceptionHandlerTest.ProbeController.class })
@WithMockUser
class GlobalExceptionHandlerTest {

	@Autowired
	private MockMvc mvc;

	@Test
	void businessExceptionUsesItsCodeStatusAndMessage() throws Exception {
		mvc.perform(get("/api/v1/probe/business").header(CorrelationId.CORRELATION_HEADER, "corr-12345678"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
			.andExpect(jsonPath("$.message").value("Investment amount is below minimum allowed amount"))
			.andExpect(jsonPath("$.status").value(422))
			.andExpect(jsonPath("$.path").value("/api/v1/probe/business"))
			.andExpect(jsonPath("$.correlationId").value("corr-12345678"))
			.andExpect(jsonPath("$.timestamp").isString())
			.andExpect(jsonPath("$.fieldErrors").doesNotExist());
	}

	@Test
	void notFoundException() throws Exception {
		mvc.perform(get("/api/v1/probe/missing"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"))
			.andExpect(jsonPath("$.message").value("Order not found: ORD-1"));
	}

	@Test
	void bodyValidationReportsEveryFieldError() throws Exception {
		mvc.perform(post("/api/v1/probe/validate").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"\",\"amount\":-5}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors", hasSize(2)));
	}

	@Test
	void malformedJsonDoesNotLeakParserDetails() throws Exception {
		mvc.perform(post("/api/v1/probe/validate").contentType(MediaType.APPLICATION_JSON).content("{not json"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
			.andExpect(jsonPath("$.message").value("Request body is missing or malformed"));
	}

	@Test
	void optimisticLockFailureMapsToConflict() throws Exception {
		mvc.perform(get("/api/v1/probe/stale"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
	}

	@Test
	void methodSecurityDenialIsForbiddenNotServerError() throws Exception {
		mvc.perform(get("/api/v1/probe/approve"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	@WithMockUser(authorities = "WITHDRAWAL_APPROVE")
	void methodSecurityAllowsHolderOfPermission() throws Exception {
		mvc.perform(get("/api/v1/probe/approve")).andExpect(status().isOk());
	}

	@Test
	void unexpectedExceptionHidesInternals() throws Exception {
		mvc.perform(get("/api/v1/probe/boom"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andExpect(jsonPath("$.message").value(not(containsString("secret"))));
	}

	@Test
	void unsupportedMethodAndUnknownPath() throws Exception {
		mvc.perform(post("/api/v1/probe/missing"))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
		mvc.perform(get("/api/v1/does-not-exist"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"))
			.andExpect(header().exists(CorrelationId.CORRELATION_HEADER));
	}

	record ProbeRequest(@NotBlank String name, @Positive BigDecimal amount) {
	}

	@RestController
	@RequestMapping("/api/v1/probe")
	static class ProbeController {

		@GetMapping("/business")
		String business() {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Investment amount is below minimum allowed amount");
		}

		@GetMapping("/missing")
		String missing() {
			throw new ResourceNotFoundException("Order", "ORD-1");
		}

		@PostMapping("/validate")
		String validate(@Valid @RequestBody ProbeRequest request) {
			return "ok";
		}

		@GetMapping("/stale")
		String stale() {
			throw new ObjectOptimisticLockingFailureException("Investment", "id-1");
		}

		@GetMapping("/approve")
		@PreAuthorize("hasAuthority('WITHDRAWAL_APPROVE')")
		String approve() {
			return "approved";
		}

		@GetMapping("/boom")
		String boom() {
			throw new IllegalStateException("secret connection string jdbc:postgresql://...");
		}

	}

}

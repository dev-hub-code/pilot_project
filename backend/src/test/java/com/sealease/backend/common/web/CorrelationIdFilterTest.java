package com.sealease.backend.common.web;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

	private final CorrelationIdFilter filter = new CorrelationIdFilter();

	@Test
	void propagatesValidIncomingCorrelationId() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
		request.addHeader(CorrelationId.CORRELATION_HEADER, "web-3f2a9c1e-77");
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicReference<String> seenInMdc = new AtomicReference<>();

		filter.doFilter(request, response, (req, res) -> seenInMdc.set(MDC.get(CorrelationId.CORRELATION_MDC_KEY)));

		assertThat(seenInMdc.get()).isEqualTo("web-3f2a9c1e-77");
		assertThat(response.getHeader(CorrelationId.CORRELATION_HEADER)).isEqualTo("web-3f2a9c1e-77");
		assertThat(response.getHeader(CorrelationId.REQUEST_HEADER)).isNotBlank();
	}

	@Test
	void replacesUnsafeIncomingCorrelationId() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
		request.addHeader(CorrelationId.CORRELATION_HEADER, "abc\r\nInjected: header");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		String correlationId = response.getHeader(CorrelationId.CORRELATION_HEADER);
		assertThat(correlationId).isNotBlank().doesNotContain("Injected");
		assertThat(CorrelationId.isValid(correlationId)).isTrue();
	}

	@Test
	void generatesIdsWhenAbsentAndClearsMdcAfterwards() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(new MockHttpServletRequest(), response, new MockFilterChain());

		assertThat(response.getHeader(CorrelationId.CORRELATION_HEADER)).isNotBlank();
		assertThat(response.getHeader(CorrelationId.REQUEST_HEADER))
			.isNotEqualTo(response.getHeader(CorrelationId.CORRELATION_HEADER));
		assertThat(MDC.get(CorrelationId.CORRELATION_MDC_KEY)).isNull();
		assertThat(MDC.get(CorrelationId.REQUEST_MDC_KEY)).isNull();
	}

}

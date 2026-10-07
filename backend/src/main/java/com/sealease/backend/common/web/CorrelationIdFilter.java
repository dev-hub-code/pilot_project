package com.sealease.backend.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Establishes correlation and request IDs before anything else runs - including Spring Security -
 * so that every log line and every error response (401/403 included) can be traced.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String incoming = request.getHeader(CorrelationId.CORRELATION_HEADER);
		String correlationId = CorrelationId.isValid(incoming) ? incoming : UUID.randomUUID().toString();
		String requestId = UUID.randomUUID().toString();

		MDC.put(CorrelationId.CORRELATION_MDC_KEY, correlationId);
		MDC.put(CorrelationId.REQUEST_MDC_KEY, requestId);
		response.setHeader(CorrelationId.CORRELATION_HEADER, correlationId);
		response.setHeader(CorrelationId.REQUEST_HEADER, requestId);
		try {
			filterChain.doFilter(request, response);
		}
		finally {
			MDC.remove(CorrelationId.CORRELATION_MDC_KEY);
			MDC.remove(CorrelationId.REQUEST_MDC_KEY);
		}
	}

}

package com.sealease.backend.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * Network origin of the current request, used for audit trails, session records and throttling.
 *
 * <p>{@code remoteAddr} is resolved by Tomcat's RemoteIpValve: {@code X-Forwarded-For} is only
 * honoured from trusted proxies ({@code server.tomcat.remoteip.internal-proxies}), taking the
 * right-most untrusted address, so a client-supplied header cannot spoof it.
 */
public record ClientInfo(String ipAddress, String userAgent) {

	private static final int MAX_USER_AGENT = 512;
	private static final int MAX_IP = 45;

	public static final ClientInfo UNKNOWN = new ClientInfo(null, null);

	public static ClientInfo from(HttpServletRequest request) {
		return new ClientInfo(truncate(request.getRemoteAddr(), MAX_IP),
				truncate(request.getHeader("User-Agent"), MAX_USER_AGENT));
	}

	/** Client info of the request bound to the current thread, or {@link #UNKNOWN} outside a request. */
	public static ClientInfo current() {
		return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
			.filter(ServletRequestAttributes.class::isInstance)
			.map(attributes -> from(((ServletRequestAttributes) attributes).getRequest()))
			.orElse(UNKNOWN);
	}

	private static String truncate(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max);
	}

}

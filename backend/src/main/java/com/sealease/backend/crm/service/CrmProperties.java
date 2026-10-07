package com.sealease.backend.crm.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param publicFormPerIp interest-form submissions accepted per client IP and window
 */
@ConfigurationProperties(prefix = "app.crm")
public record CrmProperties(@DefaultValue Limit publicFormPerIp) {

	public record Limit(@DefaultValue("5") int requests, @DefaultValue("1h") Duration window) {
	}

}

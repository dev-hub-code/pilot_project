package com.sealease.backend.container.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Locale;

/**
 * @param ownerCode the ISO 6346 owner code (3 letters) of the container numbers finance generates, e.g. SLS for
 * SLSU2000014
 */
@ConfigurationProperties(prefix = "app.containers")
public record ContainerProperties(@DefaultValue("SLS") String ownerCode) {

	public ContainerProperties {
		ownerCode = ownerCode == null ? null : ownerCode.strip().toUpperCase(Locale.ROOT);
		if (ownerCode == null || !ownerCode.matches("[A-Z]{3}")) {
			throw new IllegalArgumentException("app.containers.owner-code must be 3 letters");
		}
	}

}

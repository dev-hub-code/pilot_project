package com.sealease.backend.bankaccount.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;

/**
 * The online IFSC directory that Indian bank accounts are checked against.
 *
 * @param enabled  off: IFSC codes are checked by their format only
 * @param baseUrl  the directory; {@code GET {baseUrl}/{IFSC}} answers the branch as JSON, or 404 for an unknown code
 * @param timeout  how long to wait for it; when it does not answer, the account is accepted and checked by hand
 */
@ConfigurationProperties(prefix = "app.bank-accounts.ifsc-lookup")
public record IfscProperties(@DefaultValue("true") boolean enabled,
		@DefaultValue("https://ifsc.razorpay.com") URI baseUrl,
		@DefaultValue("5s") Duration timeout) {
}

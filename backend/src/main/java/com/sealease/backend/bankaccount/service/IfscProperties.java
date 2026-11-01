package com.sealease.backend.bankaccount.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;

/**
 * The online IFSC directory that bank names are filled in from.
 *
 * @param enabled off: accounts are saved without a bank name
 * @param baseUrl the directory; {@code GET {baseUrl}/{IFSC}} answers the branch as JSON, or 404 for an unknown code
 * @param timeout how long to wait for it; when it does not answer, the account is saved without a bank name
 */
@ConfigurationProperties(prefix = "app.bank-accounts.ifsc-lookup")
public record IfscProperties(@DefaultValue("true") boolean enabled,
		@DefaultValue("https://ifsc.razorpay.com") URI baseUrl,
		@DefaultValue("3s") Duration timeout) {
}

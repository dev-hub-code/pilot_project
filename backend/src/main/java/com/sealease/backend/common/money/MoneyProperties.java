package com.sealease.backend.common.money;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Currency;

/**
 * @param defaultCurrency platform settlement currency, used where a request does not carry one
 */
@ConfigurationProperties(prefix = "app.money")
public record MoneyProperties(@DefaultValue("USD") Currency defaultCurrency) {
}

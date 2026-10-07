package com.sealease.backend.common.money;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Currency;

/**
 * @param defaultCurrency the platform's single currency (INR): every amount on the platform is in it
 */
@ConfigurationProperties(prefix = "app.money")
public record MoneyProperties(@DefaultValue("INR") Currency defaultCurrency) {
}

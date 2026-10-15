package com.sealease.backend.kyc.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** @param after how long after registering an investor who has not verified their identity gets a support ticket */
@ConfigurationProperties(prefix = "app.kyc.follow-up")
public record KycFollowUpProperties(@DefaultValue("24h") Duration after) {
}

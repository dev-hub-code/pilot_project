package com.sealease.backend.user.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** @param temporaryPasswordValidity how long an administrator-issued temporary password can be used */
@ConfigurationProperties(prefix = "app.users.staff")
public record StaffAccountProperties(@DefaultValue("72h") Duration temporaryPasswordValidity) {
}

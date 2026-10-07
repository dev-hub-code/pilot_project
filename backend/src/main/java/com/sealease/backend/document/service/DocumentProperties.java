package com.sealease.backend.document.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/** @param maxFileSize largest accepted upload; must not exceed spring.servlet.multipart.max-file-size */
@ConfigurationProperties(prefix = "app.documents")
public record DocumentProperties(@DefaultValue("5MB") DataSize maxFileSize) {
}

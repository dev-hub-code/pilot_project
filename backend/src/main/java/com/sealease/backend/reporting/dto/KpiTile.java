package com.sealease.backend.reporting.dto;

import java.util.List;

/**
 * One figure on the admin overview.
 *
 * @param values one or more formatted values (e.g. one per currency)
 * @param link   where to see the detail in the web app
 */
public record KpiTile(String key, String label, List<String> values, String note, String link) {
}

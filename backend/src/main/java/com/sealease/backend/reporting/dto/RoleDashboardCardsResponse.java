package com.sealease.backend.reporting.dto;

import java.time.Instant;
import java.util.List;

/**
 * @param configured false while the role shows every card (the default); {@code cards} then lists them all
 * @param cards      the cards the role shows
 */
public record RoleDashboardCardsResponse(boolean configured, List<String> cards, Instant updatedAt) {
}

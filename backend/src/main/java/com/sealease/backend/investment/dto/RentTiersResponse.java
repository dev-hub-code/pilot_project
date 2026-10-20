package com.sealease.backend.investment.dto;

import java.time.Instant;
import java.util.List;

/** The rent bands, lowest first; the last has no upper limit. Orders below the first earn no rent. */
public record RentTiersResponse(List<RentTier> tiers, Instant updatedAt, String updatedByName) {
}

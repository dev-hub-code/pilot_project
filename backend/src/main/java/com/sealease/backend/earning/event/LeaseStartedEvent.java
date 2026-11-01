package com.sealease.backend.earning.event;

import java.util.UUID;

/** Published in-process when a holding's lease starts, inside that transaction. */
public record LeaseStartedEvent(UUID holdingId, UUID userId, UUID productId) {
}

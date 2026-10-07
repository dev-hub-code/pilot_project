package com.sealease.backend.user.event;

import com.sealease.backend.user.entity.UserStatus;

import java.util.UUID;

/** Published synchronously inside the transaction that changes an account's status. */
public record UserStatusChangedEvent(UUID userId, UserStatus previousStatus, UserStatus newStatus) {
}

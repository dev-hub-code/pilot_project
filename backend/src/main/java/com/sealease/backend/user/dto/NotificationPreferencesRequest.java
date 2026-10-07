package com.sealease.backend.user.dto;

import jakarta.validation.constraints.NotNull;

/** How the user wants to be told about investments, earnings and withdrawals. */
public record NotificationPreferencesRequest(@NotNull Boolean emailNotifications, @NotNull Boolean smsNotifications) {
}

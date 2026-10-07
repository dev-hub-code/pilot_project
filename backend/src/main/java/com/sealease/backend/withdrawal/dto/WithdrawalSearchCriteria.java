package com.sealease.backend.withdrawal.dto;

import com.sealease.backend.withdrawal.entity.WithdrawalStatus;

import java.util.UUID;

public record WithdrawalSearchCriteria(WithdrawalStatus status, String currency, UUID userId) {
}

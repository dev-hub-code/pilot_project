package com.sealease.backend.earning.dto;

import com.sealease.backend.earning.entity.ReceiptStatus;

import java.util.UUID;

public record RentalSearchCriteria(ReceiptStatus status, UUID productId) {
}

package com.sealease.backend.payment.dto;

import com.sealease.backend.payment.entity.PaymentMethod;
import com.sealease.backend.payment.entity.PaymentStatus;

public record PaymentSearchCriteria(PaymentStatus status, PaymentMethod method) {
}

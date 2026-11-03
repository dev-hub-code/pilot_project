package com.sealease.backend.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** What the gateway's checkout hands back once the investor has paid; its signature proves it came from the gateway. */
public record VerifyCheckoutRequest(
		@NotBlank @Size(max = 100) String gatewayOrderId,
		@NotBlank @Size(max = 100) String gatewayPaymentId,
		@NotBlank @Size(max = 200) String signature) {
}

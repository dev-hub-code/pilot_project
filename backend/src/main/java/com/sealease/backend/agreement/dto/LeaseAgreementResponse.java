package com.sealease.backend.agreement.dto;

import com.sealease.backend.agreement.entity.AgreementAcceptance;
import com.sealease.backend.agreement.entity.LeaseAgreement;

import java.time.Instant;
import java.util.UUID;

/**
 * An issued agreement, without its wording (which is downloaded as a PDF).
 *
 * @param acceptanceRequired the lease starts only once the investor accepts it
 * @param acceptedAt         when the investor accepted it; {@code null} until then, and for agreements needing none
 */
public record LeaseAgreementResponse(UUID id, String agreementNumber, UUID holdingId, UUID orderId, String containerNumber,
		String title, Instant generatedAt, boolean acceptanceRequired, Instant acceptedAt) {

	public static LeaseAgreementResponse from(LeaseAgreement a, AgreementAcceptance acceptance) {
		return new LeaseAgreementResponse(a.getId(), a.getAgreementNumber(), a.getHoldingId(), a.getOrderId(),
				a.getContainerNumber(), a.getTitle(), a.getGeneratedAt(), a.isAcceptanceRequired(),
				acceptance == null ? null : acceptance.getAcceptedAt());
	}

	/** Awaiting the investor's acceptance. */
	public boolean awaitingAcceptance() {
		return acceptanceRequired && acceptedAt == null;
	}

}

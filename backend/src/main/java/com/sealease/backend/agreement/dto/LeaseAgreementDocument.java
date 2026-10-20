package com.sealease.backend.agreement.dto;

/**
 * An agreement with its wording, for the investor to read before accepting it.
 *
 * @param body the wording as issued; the lease dates read "the day this Agreement is accepted" until it is
 */
public record LeaseAgreementDocument(LeaseAgreementResponse agreement, String body) {
}

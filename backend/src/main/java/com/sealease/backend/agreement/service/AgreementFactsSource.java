package com.sealease.backend.agreement.service;

import java.util.UUID;

/**
 * Looks up the facts of a holding's investment again, so that an agreement not yet accepted can be shown in the
 * wording now in force. Implemented by the module that owns orders and holdings.
 */
public interface AgreementFactsSource {

	/**
	 * The facts as they stood before the investor accepted: without lease dates, which acceptance sets.
	 *
	 * @param userId the holding's owner
	 */
	AgreementFacts pendingFacts(UUID holdingId, UUID userId);

}

package com.sealease.backend.kyc.dto;

import com.sealease.backend.document.entity.DocumentPurpose;
import com.sealease.backend.kyc.entity.IdentityDocumentType;
import com.sealease.backend.kyc.entity.KycDocument;
import com.sealease.backend.kyc.entity.KycSubmission;
import com.sealease.backend.kyc.entity.KycSubmissionStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * A KYC submission, with the full (decrypted) identity numbers, for its owner and for reviewers; reviewers' views are
 * audited. Submissions carry the Aadhaar and PAN numbers; those made before carry a single identity document
 * ({@code documentType} and the fields after it).
 */
public record KycSubmissionResponse(
		UUID id,
		UUID userId,
		KycSubmissionStatus status,
		String legalFirstName,
		String legalLastName,
		LocalDate dateOfBirth,
		String nationality,
		String aadhaarNumber,
		String panNumber,
		IdentityDocumentType documentType,
		String documentNumber,
		String documentIssuingCountry,
		LocalDate documentExpiryDate,
		Instant submittedAt,
		Instant reviewedAt,
		UUID reviewedBy,
		String rejectionReason,
		List<DocumentRef> documents) {

	public record DocumentRef(UUID id, DocumentPurpose purpose) {
	}

	/** The submission's identity numbers, decrypted; null where it has none. */
	public record Numbers(String aadhaar, String pan, String document) {
	}

	public static KycSubmissionResponse from(KycSubmission s, List<KycDocument> documents, Numbers numbers) {
		List<DocumentRef> refs = documents.stream()
			.sorted(Comparator.comparing(KycDocument::getPurpose))
			.map(d -> new DocumentRef(d.getId().documentId(), d.getPurpose()))
			.toList();
		return new KycSubmissionResponse(s.getId(), s.getUserId(), s.getStatus(), s.getLegalFirstName(),
				s.getLegalLastName(), s.getDateOfBirth(), s.getNationality(),
				numbers.aadhaar(), numbers.pan(), s.getDocumentType(), numbers.document(),
				s.getDocumentIssuingCountry(), s.getDocumentExpiryDate(),
				s.getSubmittedAt(), s.getReviewedAt(), s.getReviewedBy(), s.getRejectionReason(), refs);
	}

	/** The investor's own view omits reviewer identity. */
	public KycSubmissionResponse forOwner() {
		return new KycSubmissionResponse(id, userId, status, legalFirstName, legalLastName, dateOfBirth, nationality,
				aadhaarNumber, panNumber, documentType, documentNumber, documentIssuingCountry, documentExpiryDate, submittedAt,
				reviewedAt, null, rejectionReason, documents);
	}

}

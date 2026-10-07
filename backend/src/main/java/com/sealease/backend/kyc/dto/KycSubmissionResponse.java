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

/** A KYC submission. The identity document number is only ever returned masked. */
public record KycSubmissionResponse(
		UUID id,
		UUID userId,
		KycSubmissionStatus status,
		String legalFirstName,
		String legalLastName,
		LocalDate dateOfBirth,
		String nationality,
		IdentityDocumentType documentType,
		String documentNumberMasked,
		String documentIssuingCountry,
		LocalDate documentExpiryDate,
		Instant submittedAt,
		Instant reviewedAt,
		UUID reviewedBy,
		String rejectionReason,
		List<DocumentRef> documents) {

	public record DocumentRef(UUID id, DocumentPurpose purpose) {
	}

	public static KycSubmissionResponse from(KycSubmission s, List<KycDocument> documents) {
		List<DocumentRef> refs = documents.stream()
			.sorted(Comparator.comparing(KycDocument::getPurpose))
			.map(d -> new DocumentRef(d.getId().documentId(), d.getPurpose()))
			.toList();
		return new KycSubmissionResponse(s.getId(), s.getUserId(), s.getStatus(), s.getLegalFirstName(),
				s.getLegalLastName(), s.getDateOfBirth(), s.getNationality(), s.getDocumentType(),
				"•••• " + s.getDocumentNumberLast4(), s.getDocumentIssuingCountry(), s.getDocumentExpiryDate(),
				s.getSubmittedAt(), s.getReviewedAt(), s.getReviewedBy(), s.getRejectionReason(), refs);
	}

	/** The investor's own view omits reviewer identity. */
	public KycSubmissionResponse forOwner() {
		return new KycSubmissionResponse(id, userId, status, legalFirstName, legalLastName, dateOfBirth, nationality,
				documentType, documentNumberMasked, documentIssuingCountry, documentExpiryDate, submittedAt,
				reviewedAt, null, rejectionReason, documents);
	}

}

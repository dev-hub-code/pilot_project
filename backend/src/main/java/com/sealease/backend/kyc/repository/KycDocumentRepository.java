package com.sealease.backend.kyc.repository;

import com.sealease.backend.kyc.entity.KycDocument;
import com.sealease.backend.kyc.entity.KycDocumentId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KycDocumentRepository extends JpaRepository<KycDocument, KycDocumentId> {

	List<KycDocument> findByIdSubmissionId(UUID submissionId);

	boolean existsById(KycDocumentId id);

	/** A draft's document of one kind, replaced when the investor uploads it again. */
	@org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
	@org.springframework.data.jpa.repository.Query("delete from KycDocument d where d.id.submissionId = :submissionId and d.purpose = :purpose")
	int deleteFromSubmission(@org.springframework.data.repository.query.Param("submissionId") UUID submissionId,
			@org.springframework.data.repository.query.Param("purpose") com.sealease.backend.document.entity.DocumentPurpose purpose);

}

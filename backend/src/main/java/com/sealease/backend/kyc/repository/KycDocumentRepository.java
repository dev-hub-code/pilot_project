package com.sealease.backend.kyc.repository;

import com.sealease.backend.kyc.entity.KycDocument;
import com.sealease.backend.kyc.entity.KycDocumentId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KycDocumentRepository extends JpaRepository<KycDocument, KycDocumentId> {

	List<KycDocument> findByIdSubmissionId(UUID submissionId);

	boolean existsById(KycDocumentId id);

}

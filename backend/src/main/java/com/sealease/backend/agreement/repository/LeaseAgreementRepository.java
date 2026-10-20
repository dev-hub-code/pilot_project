package com.sealease.backend.agreement.repository;

import com.sealease.backend.agreement.entity.LeaseAgreement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaseAgreementRepository extends JpaRepository<LeaseAgreement, UUID> {

	Optional<LeaseAgreement> findByHoldingId(UUID holdingId);


	List<LeaseAgreement> findByUserIdOrderByGeneratedAtDesc(UUID userId);

	List<LeaseAgreement> findByOrderIdOrderByGeneratedAt(UUID orderId);

	@Query(value = "select nextval('agreement_number_seq')", nativeQuery = true)
	long nextNumber();

}

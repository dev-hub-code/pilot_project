package com.sealease.backend.user.repository;

import com.sealease.backend.user.entity.InvestorClassification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InvestorClassificationRepository extends JpaRepository<InvestorClassification, UUID> {

	List<InvestorClassification> findByUserIdOrderByDecidedAtDesc(UUID userId);

}

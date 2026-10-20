package com.sealease.backend.agreement.repository;

import com.sealease.backend.agreement.entity.AgreementTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgreementTemplateRepository extends JpaRepository<AgreementTemplate, UUID> {

	/** The version in force. */
	Optional<AgreementTemplate> findTopByOrderByVersionDesc();

	List<AgreementTemplate> findTop20ByOrderByVersionDesc();

}

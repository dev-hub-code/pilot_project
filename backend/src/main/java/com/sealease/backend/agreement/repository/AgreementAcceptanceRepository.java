package com.sealease.backend.agreement.repository;

import com.sealease.backend.agreement.entity.AgreementAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AgreementAcceptanceRepository extends JpaRepository<AgreementAcceptance, UUID> {
}

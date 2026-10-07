package com.sealease.backend.crm.repository;

import com.sealease.backend.crm.entity.LeadActivity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LeadActivityRepository extends JpaRepository<LeadActivity, UUID> {

	List<LeadActivity> findByLeadIdOrderByCreatedAtDescIdDesc(UUID leadId, Pageable pageable);

}

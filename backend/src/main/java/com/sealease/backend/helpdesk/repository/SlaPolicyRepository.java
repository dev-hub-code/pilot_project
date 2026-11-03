package com.sealease.backend.helpdesk.repository;

import com.sealease.backend.helpdesk.entity.SlaPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, UUID> {

	List<SlaPolicy> findByActiveTrue();

	List<SlaPolicy> findAllByOrderByNameAsc();

}

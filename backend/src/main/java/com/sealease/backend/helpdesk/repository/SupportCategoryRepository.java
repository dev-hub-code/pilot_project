package com.sealease.backend.helpdesk.repository;

import com.sealease.backend.helpdesk.entity.SupportCategory;
import com.sealease.backend.helpdesk.entity.TicketCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupportCategoryRepository extends JpaRepository<SupportCategory, UUID> {

	List<SupportCategory> findAllByOrderBySortOrderAscNameAsc();

	Optional<SupportCategory> findByCode(String code);

	Optional<SupportCategory> findByParentIdIsNullAndCategory(TicketCategory category);

}

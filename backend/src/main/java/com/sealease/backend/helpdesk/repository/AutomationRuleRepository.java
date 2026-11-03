package com.sealease.backend.helpdesk.repository;

import com.sealease.backend.helpdesk.entity.AutomationRule;
import com.sealease.backend.helpdesk.entity.AutomationTrigger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AutomationRuleRepository extends JpaRepository<AutomationRule, UUID> {

	List<AutomationRule> findByTriggerAndEnabledTrue(AutomationTrigger trigger);

	List<AutomationRule> findAllByOrderByCreatedAtAsc();

}

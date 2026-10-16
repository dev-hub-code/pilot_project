package com.sealease.backend.reporting.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.reporting.dto.RoleDashboardCardsResponse;
import com.sealease.backend.reporting.entity.RoleDashboardCards;
import com.sealease.backend.reporting.repository.RoleDashboardCardsRepository;
import com.sealease.backend.role.service.RoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Which dashboard cards each role shows, chosen by admins (ROLE_MANAGE). A role that has not chosen shows every card.
 * An employee sees the cards any of their roles shows, and then arranges those on their own dashboard.
 */
@Service
public class RoleDashboardCardService {

	private static final String ENTITY = "ROLE";

	private final RoleDashboardCardsRepository roleCards;
	private final RoleService roles;
	private final AuditService audit;
	private final Clock clock;

	public RoleDashboardCardService(RoleDashboardCardsRepository roleCards, RoleService roles, AuditService audit,
			Clock clock) {
		this.roleCards = roleCards;
		this.roles = roles;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public RoleDashboardCardsResponse get(UUID roleId) {
		roles.get(roleId);
		return roleCards.findById(roleId)
			.map(c -> new RoleDashboardCardsResponse(true, c.getCards(), c.getUpdatedAt()))
			.orElseGet(() -> new RoleDashboardCardsResponse(false,
					DashboardCards.ALL.stream().map(DashboardCards.Card::key).toList(), null));
	}

	@Transactional
	public RoleDashboardCardsResponse choose(UUID actorId, UUID roleId, List<String> cards) {
		String name = roles.get(roleId).name();
		Set<String> chosen = new LinkedHashSet<>();
		for (String key : cards) {
			if (!DashboardCards.KEYS.contains(key)) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "There is no dashboard card " + key);
			}
			chosen.add(key);
		}
		Optional<RoleDashboardCards> existing = roleCards.findById(roleId);
		Optional<List<String>> before = existing.map(RoleDashboardCards::getCards);
		RoleDashboardCards row = existing.orElseGet(() -> new RoleDashboardCards(roleId));
		row.choose(List.copyOf(chosen), actorId, clock.instant());
		roleCards.saveAndFlush(row);
		audit.record(AuditRecord.of(actorId, AuditAction.ROLE_DASHBOARD_CARDS_CHANGED, ENTITY, roleId)
			.withOldValue(Map.of("role", name, "cards", before.map(Object::toString).orElse("all")))
			.withNewValue(Map.of("role", name, "cards", List.copyOf(chosen))));
		return get(roleId);
	}

	/** Back to showing every card. */
	@Transactional
	public RoleDashboardCardsResponse reset(UUID actorId, UUID roleId) {
		String name = roles.get(roleId).name();
		if (roleCards.existsById(roleId)) {
			roleCards.deleteById(roleId);
			audit.record(AuditRecord.of(actorId, AuditAction.ROLE_DASHBOARD_CARDS_CHANGED, ENTITY, roleId)
				.withNewValue(Map.of("role", name, "cards", "all")));
		}
		return get(roleId);
	}

	/** The cards the user's roles show between them; empty when one of them shows every card. */
	@Transactional(readOnly = true)
	public Optional<List<String>> allowedFor(UUID userId) {
		Set<String> allowed = new HashSet<>();
		for (String cards : roleCards.cardsOfRolesOf(userId)) {
			if (cards == null) {
				return Optional.empty();
			}
			if (!cards.isEmpty()) {
				allowed.addAll(Arrays.asList(cards.split(",")));
			}
		}
		return Optional.of(DashboardCards.ALL.stream().map(DashboardCards.Card::key).filter(allowed::contains).toList());
	}

}

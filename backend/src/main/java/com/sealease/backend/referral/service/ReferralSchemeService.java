package com.sealease.backend.referral.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.referral.dto.ReferralScheme;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Which referral scheme an investor is on. Network investors are listed in {@code network_investors}; everyone else is
 * on the online scheme. A network investor's setting decides whether the people who sign up with their code join the
 * network or the online scheme; those who join the network pass the same setting on.
 */
@Service
public class ReferralSchemeService {

	private static final String ENTITY = "REFERRAL";

	private final JdbcTemplate jdbc;
	private final AuditService audit;
	private final Clock clock;

	public ReferralSchemeService(JdbcTemplate jdbc, AuditService audit, Clock clock) {
		this.jdbc = jdbc;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public ReferralScheme schemeOf(UUID userId) {
		return setting(userId).isEmpty() ? ReferralScheme.ONLINE : ReferralScheme.NETWORK;
	}

	/** Whether people signing up with this investor's code join the network. False for online investors. */
	@Transactional(readOnly = true)
	public boolean signupsJoinNetwork(UUID userId) {
		return setting(userId).stream().findFirst().orElse(false);
	}

	/** Puts an account made by an administrator on the network scheme. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void enrol(UUID actorId, UUID userId, boolean signupsJoinNetwork) {
		insert(actorId, userId, signupsJoinNetwork);
		audit.record(AuditRecord.of(actorId, AuditAction.REFERRAL_NETWORK_JOINED, ENTITY, userId)
			.withNewValue(Map.of("signupsJoinNetwork", signupsJoinNetwork)));
	}

	/** A new account just linked to {@code referrerId}: joins the network if the referrer's setting says so. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void onLinked(UUID newUserId, UUID referrerId) {
		if (signupsJoinNetwork(referrerId)) {
			insert(null, newUserId, true);
			audit.record(AuditRecord.of(newUserId, AuditAction.REFERRAL_NETWORK_JOINED, ENTITY, newUserId)
				.withNewValue(Map.of("referrerId", referrerId.toString(), "signupsJoinNetwork", true)));
		}
	}

	private void insert(UUID actorId, UUID userId, boolean signupsJoinNetwork) {
		jdbc.update("""
				INSERT INTO network_investors (user_id, signups_join_network, created_by, created_at)
				VALUES (?, ?, ?, ?)
				ON CONFLICT DO NOTHING
				""", userId, signupsJoinNetwork, actorId, Timestamp.from(clock.instant()));
	}

	private List<Boolean> setting(UUID userId) {
		return jdbc.queryForList("SELECT signups_join_network FROM network_investors WHERE user_id = ?", Boolean.class,
				userId);
	}

}

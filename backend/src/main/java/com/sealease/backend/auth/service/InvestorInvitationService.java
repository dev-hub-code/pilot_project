package com.sealease.backend.auth.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.auth.config.AuthProperties;
import com.sealease.backend.auth.dto.AcceptInvitationRequest;
import com.sealease.backend.auth.dto.InvitationDetails;
import com.sealease.backend.auth.dto.InviteInvestorRequest;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.validation.PhoneNumbers;
import com.sealease.backend.referral.service.ReferralSchemeService;
import com.sealease.backend.role.service.UserRoleService;
import com.sealease.backend.terms.service.TermsService;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.service.EmailNormalizer;
import com.sealease.backend.user.service.UserAccountService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Invitations to open a network investor account, which any employee can send to a client's email address. The client
 * follows the emailed link and fills in their own details and password: unlike an account an administrator makes, no
 * temporary password changes hands. The link works once, for {@link #VALIDITY}; a newer invitation to the same address
 * retires the earlier ones. An invited client has no referrer: the employee who invites them is not one, so employees
 * earn no referral income on the clients they invite.
 */
@Service
public class InvestorInvitationService {

	public static final Duration VALIDITY = Duration.ofDays(7);

	private static final String ENTITY = "INVESTOR_INVITATION";
	private static final String INVALID = "This invitation link is invalid, has expired or has already been used."
			+ " Ask SeaLease for a new one.";

	private final JdbcTemplate jdbc;
	private final UserAccountService accounts;
	private final UserRoleService userRoles;
	private final ReferralSchemeService schemes;
	private final TermsService terms;
	private final AuditService audit;
	private final AuthProperties properties;
	private final Clock clock;

	public InvestorInvitationService(JdbcTemplate jdbc, UserAccountService accounts, UserRoleService userRoles,
			ReferralSchemeService schemes, TermsService terms, AuditService audit,
			AuthProperties properties, Clock clock) {
		this.jdbc = jdbc;
		this.accounts = accounts;
		this.userRoles = userRoles;
		this.schemes = schemes;
		this.terms = terms;
		this.audit = audit;
		this.properties = properties;
		this.clock = clock;
	}

	/** A new invitation, with the raw token for the email link: only its hash is stored. */
	public record IssuedInvitation(UUID id, String email, String firstName, String invitedBy, String token,
			Instant expiresAt) {

		@Override
		public String toString() {
			return "IssuedInvitation[id=" + id + ", email=" + email + "]";
		}

	}

	@Transactional
	public IssuedInvitation invite(UUID actorId, InviteInvestorRequest request) {
		String email = EmailNormalizer.normalize(request.email());
		if (accounts.findByEmail(email).isPresent()) {
			throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED, "An account with this email already exists");
		}
		Instant now = clock.instant();
		jdbc.update("""
				UPDATE investor_invitations SET revoked_at = ?
				WHERE email = ? AND accepted_at IS NULL AND revoked_at IS NULL
				""", Timestamp.from(now), email);

		UUID id = UUID.randomUUID();
		String token = OpaqueTokens.generate();
		Instant expiresAt = now.plus(VALIDITY);
		String firstName = request.firstName().strip();
		jdbc.update("""
				INSERT INTO investor_invitations (id, email, first_name, last_name, signups_join_network,
				    token_hash, invited_by, created_at, expires_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", id, email, firstName, request.lastName().strip(), request.signupsJoinNetwork(),
				OpaqueTokens.hash(token), actorId, Timestamp.from(now), Timestamp.from(expiresAt));

		audit.record(AuditRecord.of(actorId, AuditAction.INVESTOR_INVITATION_SENT, ENTITY, id)
			.withNewValue(Map.of("email", email, "signupsJoinNetwork", request.signupsJoinNetwork())));
		return new IssuedInvitation(id, email, firstName, accounts.getAccount(actorId).firstName(), token, expiresAt);
	}

	/** What the sign-up page shows for a link; fails as {@link #accept} would for a link that no longer works. */
	@Transactional(readOnly = true)
	public InvitationDetails details(String token) {
		Pending pending = find(token, false);
		return new InvitationDetails(pending.email(), pending.firstName(), pending.lastName(),
				accounts.getAccount(pending.invitedBy()).firstName(), pending.expiresAt());
	}

	/**
	 * Opens the invited client's account: an active network investor whose email address is confirmed (they followed
	 * the link sent to it), who accepted the terms in force. Runs in the caller's transaction, which signs them in.
	 *
	 * @return the new account's id
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public UUID accept(AcceptInvitationRequest request) {
		Pending pending = find(request.token(), true);
		UserAccount account = accounts.register(pending.email(), request.password(), request.firstName(),
				request.lastName(), PhoneNumbers.require(request.phone(), request.phoneCountry()), true);
		UUID userId = account.id();
		userRoles.grantSystemRole(userId, properties.defaultRole());
		terms.accept(userId);
		schemes.enrol(pending.invitedBy(), userId, pending.signupsJoinNetwork());
		jdbc.update("UPDATE investor_invitations SET accepted_at = ?, accepted_user_id = ? WHERE id = ?",
				Timestamp.from(clock.instant()), userId, pending.id());
		audit.record(AuditRecord.of(userId, AuditAction.INVESTOR_INVITATION_ACCEPTED, ENTITY, pending.id())
			.withNewValue(Map.of("userId", userId.toString(), "invitedBy", pending.invitedBy().toString())));
		return userId;
	}

	private record Pending(UUID id, String email, String firstName, String lastName, boolean signupsJoinNetwork, UUID invitedBy, Instant expiresAt) {
	}

	/** @param lock lock the row so the same link cannot open two accounts at once */
	private Pending find(String token, boolean lock) {
		return jdbc.query("""
				SELECT id, email, first_name, last_name, signups_join_network, invited_by, expires_at
				FROM investor_invitations
				WHERE token_hash = ? AND accepted_at IS NULL AND revoked_at IS NULL AND expires_at > ?
				""" + (lock ? " FOR UPDATE" : ""),
				(rs, i) -> new Pending(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("first_name"),
						rs.getString("last_name"), rs.getBoolean("signups_join_network"),
						rs.getObject("invited_by", UUID.class), rs.getTimestamp("expires_at").toInstant()),
				OpaqueTokens.hash(token), Timestamp.from(clock.instant()))
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_VERIFICATION_TOKEN, INVALID));
	}

}

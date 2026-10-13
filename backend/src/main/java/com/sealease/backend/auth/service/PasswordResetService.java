package com.sealease.backend.auth.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.auth.config.AuthProperties;
import com.sealease.backend.auth.entity.PasswordResetCode;
import com.sealease.backend.auth.entity.SessionRevocationReason;
import com.sealease.backend.auth.repository.PasswordResetCodeRepository;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.ratelimit.FixedWindowRateLimiter;
import com.sealease.backend.common.web.ClientInfo;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.entity.UserStatus;
import com.sealease.backend.user.service.EmailNormalizer;
import com.sealease.backend.user.service.UserAccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * "Forgot password": a 6-digit code is emailed to the account's address, and entering it with a new password
 * sets that password and signs the account out everywhere. Requests never reveal whether an address is
 * registered. Guessing is bounded: a code works for minutes, stops after a few wrong attempts, and both
 * requests and attempts are rate limited.
 */
@Service
public class PasswordResetService {

	private static final String ENTITY_USER = "USER";

	private final PasswordResetCodeRepository codes;
	private final UserAccountService accounts;
	private final SessionService sessions;
	private final PasswordResetEmailSender sender;
	private final AuditService audit;
	private final AuthProperties.PasswordReset properties;
	private final Clock clock;
	private final FixedWindowRateLimiter requestsPerIp;
	private final FixedWindowRateLimiter requestsPerAddress;
	private final FixedWindowRateLimiter attemptsPerIp;

	public PasswordResetService(PasswordResetCodeRepository codes, UserAccountService accounts, SessionService sessions,
			PasswordResetEmailSender sender, AuditService audit, AuthProperties properties, Clock clock) {
		this.codes = codes;
		this.accounts = accounts;
		this.sessions = sessions;
		this.sender = sender;
		this.audit = audit;
		this.properties = properties.passwordReset();
		this.clock = clock;
		AuthProperties.RateLimits limits = Objects.requireNonNull(properties.rateLimit(), "app.auth.rate-limit");
		this.requestsPerIp = limiter(limits.passwordResetPerIp(), clock);
		this.requestsPerAddress = limiter(limits.passwordResetPerAddress(), clock);
		this.attemptsPerIp = limiter(limits.passwordResetCodePerIp(), clock);
	}

	/**
	 * Emails a reset code to an active account with this address, retiring any earlier code. Says nothing about
	 * whether the address is registered: unknown and inactive accounts are ignored silently.
	 */
	@Transactional
	public void request(String email, ClientInfo client) {
		requestsPerIp.acquire(keyOf(client));
		requestsPerAddress.acquire(EmailNormalizer.normalize(email));
		Optional<UserAccount> found = accounts.findByEmail(email).filter(a -> a.status() == UserStatus.ACTIVE);
		if (found.isEmpty()) {
			return;
		}
		UserAccount account = found.get();
		Instant now = clock.instant();
		codes.retireUnused(account.id(), now);
		String code = VerificationCodes.generate();
		codes.save(new PasswordResetCode(account.id(), VerificationCodes.hash(account.id(), code), now,
				now.plus(properties.codeTtl())));
		audit.record(AuditRecord.of(account.id(), AuditAction.PASSWORD_RESET_REQUESTED, ENTITY_USER, account.id()));
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				sender.send(account.id(), account.email(), account.firstName(), code);
			}
		});
	}

	/**
	 * Sets a new password if {@code code} is the account's current reset code, and ends every session. Wrong
	 * guesses are counted and committed (hence {@code noRollbackFor}).
	 */
	@Transactional(noRollbackFor = BusinessException.class)
	public void reset(String email, String code, String newPassword, ClientInfo client) {
		attemptsPerIp.acquire(keyOf(client));
		UserAccount account = accounts.findByEmail(email).filter(a -> a.status() == UserStatus.ACTIVE)
			.orElseThrow(PasswordResetService::invalidCode);
		Instant now = clock.instant();
		PasswordResetCode current = codes.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(account.id())
			.filter(c -> c.isUsableAt(now))
			.orElseThrow(PasswordResetService::invalidCode);
		if (!current.hasCodeHash(VerificationCodes.hash(account.id(), code.strip()))) {
			if (current.registerFailedAttempt(properties.maxAttempts(), now)) {
				throw new BusinessException(ErrorCode.INVALID_RESET_CODE, "Too many wrong codes; request a new one");
			}
			throw invalidCode();
		}
		// The password is checked against the policy first: a weak one leaves the code usable for another try.
		accounts.resetPassword(account.id(), newPassword);
		current.markUsed(now);
		int revoked = sessions.revokeAll(account.id(), SessionRevocationReason.PASSWORD_RESET);
		audit.record(AuditRecord.of(account.id(), AuditAction.PASSWORD_RESET, ENTITY_USER, account.id())
			.withNewValue(Map.of("sessionsRevoked", revoked)));
	}

	private static BusinessException invalidCode() {
		return new BusinessException(ErrorCode.INVALID_RESET_CODE,
				"That code is not right or has expired; check the latest email or request a new code");
	}

	private static String keyOf(ClientInfo client) {
		return client.ipAddress() == null ? "unknown" : client.ipAddress();
	}

	private static FixedWindowRateLimiter limiter(AuthProperties.Limit limit, Clock clock) {
		return new FixedWindowRateLimiter(limit.requests(), limit.window(), clock);
	}

}

package com.sealease.backend.auth.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.auth.config.AuthProperties;
import com.sealease.backend.auth.entity.EmailVerificationToken;
import com.sealease.backend.auth.repository.EmailVerificationTokenRepository;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.ratelimit.FixedWindowRateLimiter;
import com.sealease.backend.common.web.ClientInfo;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.entity.UserStatus;
import com.sealease.backend.user.service.EmailNormalizer;
import com.sealease.backend.user.service.UserAccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Confirms that a self-registered user owns their email address. After sign-up (and again on request)
 * one email carries a single-use link and a 6-digit code; following the link or entering the code marks
 * the address verified. Signing in does not wait for it; the app keeps asking until it is done.
 */
@Service
public class EmailVerificationService {

	private static final String ENTITY_USER = "USER";

	private final EmailVerificationTokenRepository tokens;
	private final UserAccountService accounts;
	private final VerificationEmailSender sender;
	private final AuditService audit;
	private final AuthProperties.EmailVerification properties;
	private final Clock clock;
	private final FixedWindowRateLimiter perIp;
	private final FixedWindowRateLimiter perAddress;
	private final FixedWindowRateLimiter codesPerIp;

	public EmailVerificationService(EmailVerificationTokenRepository tokens, UserAccountService accounts,
			VerificationEmailSender sender, AuditService audit, AuthProperties properties, Clock clock) {
		this.tokens = tokens;
		this.accounts = accounts;
		this.sender = sender;
		this.audit = audit;
		this.properties = properties.emailVerification();
		this.clock = clock;
		AuthProperties.RateLimits limits = Objects.requireNonNull(properties.rateLimit(), "app.auth.rate-limit");
		this.perIp = new FixedWindowRateLimiter(limits.verificationEmailPerIp().requests(),
				limits.verificationEmailPerIp().window(), clock);
		this.perAddress = new FixedWindowRateLimiter(limits.verificationEmailPerAddress().requests(),
				limits.verificationEmailPerAddress().window(), clock);
		this.codesPerIp = new FixedWindowRateLimiter(limits.verificationCodePerIp().requests(),
				limits.verificationCodePerIp().window(), clock);
	}

	public boolean isRequired() {
		return properties.required();
	}

	/**
	 * Creates a new link for the account, retiring any earlier ones, and emails it once the caller's
	 * transaction commits - so no email goes out for an account that was rolled back.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void sendLink(UserAccount account) {
		Instant now = clock.instant();
		tokens.retireUnused(account.id(), now);
		String token = OpaqueTokens.generate();
		String code = VerificationCodes.generate();
		tokens.save(new EmailVerificationToken(account.id(), OpaqueTokens.hash(token),
				VerificationCodes.hash(account.id(), code), now, now.plus(properties.linkTtl())));
		audit.record(AuditRecord.of(account.id(), AuditAction.EMAIL_VERIFICATION_SENT, ENTITY_USER, account.id()));
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				sender.send(account.id(), account.email(), account.firstName(), token, code);
			}
		});
	}

	/**
	 * Sends a new link to an account that has not confirmed its address. Says nothing about whether
	 * the address is registered: unknown, already verified and inactive accounts are ignored silently.
	 */
	@Transactional
	public void resend(String email, ClientInfo client) {
		perIp.acquire(client.ipAddress() == null ? "unknown" : client.ipAddress());
		perAddress.acquire(EmailNormalizer.normalize(email));
		Optional<UserAccount> account = accounts.findByEmail(email);
		if (account.isPresent() && account.get().status() == UserStatus.ACTIVE
				&& !accounts.isEmailVerified(account.get().id())) {
			sendLink(account.get());
		}
	}

	/**
	 * Confirms the address the link was sent to. Following a link again after it worked is not an
	 * error, so a double click or an email scanner opening it first does not confuse the user.
	 */
	@Transactional
	public void verify(String token) {
		EmailVerificationToken link = tokens.findByTokenHashForUpdate(OpaqueTokens.hash(token))
			.orElseThrow(EmailVerificationService::invalidLink);
		Instant now = clock.instant();
		if (!link.isUsableAt(now)) {
			if (accounts.isEmailVerified(link.getUserId())) {
				return;
			}
			throw invalidLink();
		}
		link.markUsed(now);
		accounts.markEmailVerified(link.getUserId());
	}

	/**
	 * Confirms the address with the 6-digit code from the latest email. Wrong guesses are counted (and
	 * committed, hence {@code noRollbackFor}); after a few the email stops working and a new one is needed.
	 */
	@Transactional(noRollbackFor = BusinessException.class)
	public void verifyCode(String email, String code, ClientInfo client) {
		codesPerIp.acquire(client.ipAddress() == null ? "unknown" : client.ipAddress());
		UserAccount account = accounts.findByEmail(email).orElseThrow(EmailVerificationService::invalidCode);
		if (accounts.isEmailVerified(account.id())) {
			return;
		}
		Instant now = clock.instant();
		EmailVerificationToken current = tokens.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(account.id())
			.filter(t -> t.isUsableAt(now))
			.orElseThrow(EmailVerificationService::invalidCode);
		if (!current.hasCodeHash(VerificationCodes.hash(account.id(), code.strip()))) {
			if (current.registerFailedCode(properties.maxCodeAttempts(), now)) {
				throw new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE,
						"Too many wrong codes; request a new email");
			}
			throw invalidCode();
		}
		current.markUsed(now);
		accounts.markEmailVerified(account.id());
	}

	private static BusinessException invalidCode() {
		return new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE,
				"That code is not right or has expired; check the latest email or request a new one");
	}

	private static BusinessException invalidLink() {
		return new BusinessException(ErrorCode.INVALID_VERIFICATION_TOKEN,
				"This verification link is invalid or has expired; request a new one");
	}

}

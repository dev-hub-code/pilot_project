package com.sealease.backend.referral.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.referral.dto.AdminDownline;
import com.sealease.backend.referral.dto.AdminReferralView;
import com.sealease.backend.referral.dto.Downline;
import com.sealease.backend.referral.dto.DownlineMember;
import com.sealease.backend.referral.dto.ReferralOverview;
import com.sealease.backend.referral.dto.Upline;
import com.sealease.backend.referral.entity.ReferralRateVersion;
import com.sealease.backend.referral.repository.ReferralEarningRepository;
import com.sealease.backend.referral.repository.ReferralTotal;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.entity.KycStatus;
import com.sealease.backend.user.entity.UserStatus;
import com.sealease.backend.user.service.UserAccountService;
import com.sealease.backend.user.service.UserProfileService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The referral hierarchy: codes, who referred whom, and views of a user's uplines and downline.
 *
 * <p>A referrer is set at registration and must be an existing account; afterwards only a super administrator can
 * move a member (with their downline) to another referrer, never below themselves, or take them out of their
 * downline, so the hierarchy stays a forest without cycles. Only four levels earn commissions, so the walks for investors stop at depth four.
 */
@Service
public class ReferralService {

	public static final int LEVELS = ReferralRateVersion.LEVELS;
	/** Downline views list at most this many members (the earliest joiners first). */
	static final int DOWNLINE_LIMIT = 2_000;

	private static final String ENTITY = "REFERRAL";
	/** No 0/O, 1/I/L: codes are read aloud and typed by hand. */
	private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
	private static final int CODE_LENGTH = 8;
	/** Deepest level the administrator's downline view and the move check walk: far beyond any real hierarchy. */
	private static final int MAX_DEPTH = 100;
	/** Serialises moves, so two at once cannot together make a cycle. */
	private static final long MOVE_LOCK = 0x5EA1EA5E_0001L;
	/** Downline member ids: "m" + position in join order, 1-based. */
	private static final Pattern MEMBER_ID = Pattern.compile("m([1-9]\\d{0,8})");

	private static final String UPLINES = """
			WITH RECURSIVE up (user_id, referrer_id, depth) AS (
			    SELECT user_id, referrer_id, 1 FROM referrals WHERE user_id = ?
			    UNION ALL
			    SELECT r.user_id, r.referrer_id, up.depth + 1
			    FROM referrals r JOIN up ON r.user_id = up.referrer_id
			    WHERE up.depth < %d)
			SELECT referrer_id, depth FROM up ORDER BY depth
			""".formatted(LEVELS);
	private static final String DOWNLINE = """
			WITH RECURSIVE down (user_id, referrer_id, depth, created_at) AS (
			    SELECT user_id, referrer_id, 1, created_at FROM referrals WHERE referrer_id = ?
			    UNION ALL
			    SELECT r.user_id, r.referrer_id, down.depth + 1, r.created_at
			    FROM referrals r JOIN down ON r.referrer_id = down.user_id
			    WHERE down.depth < %d)
			""".formatted(LEVELS);

	private final JdbcTemplate jdbc;
	private final ReferralRateService rates;
	private final ReferralEarningRepository earnings;
	private final UserAccountService accounts;
	private final UserProfileService profiles;
	private final AuditService audit;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	public ReferralService(JdbcTemplate jdbc, ReferralRateService rates, ReferralEarningRepository earnings,
			UserAccountService accounts, UserProfileService profiles, AuditService audit, Clock clock) {
		this.jdbc = jdbc;
		this.rates = rates;
		this.earnings = earnings;
		this.accounts = accounts;
		this.profiles = profiles;
		this.audit = audit;
		this.clock = clock;
	}

	// ------------------------------------------------------------------------- linking

	/**
	 * Records who referred a newly registered account, in the registration transaction. An unknown
	 * code, or the code of an account that is no longer active, fails the registration.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void link(UUID newUserId, String code) {
		UUID referrerId = jdbc.query("SELECT user_id FROM referral_codes WHERE code = ?",
				(rs, i) -> rs.getObject("user_id", UUID.class), code).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "Referral code not recognised"));
		if (accounts.getAccount(referrerId).status() != UserStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "This referral code is no longer valid");
		}
		jdbc.update("INSERT INTO referrals (user_id, referrer_id, code, created_at) VALUES (?, ?, ?, ?)", newUserId,
				referrerId, code, now());
		audit.record(AuditRecord.of(newUserId, AuditAction.REFERRAL_LINKED, ENTITY, newUserId)
			.withNewValue(Map.of("referrerId", referrerId.toString(), "code", code)));
	}

	/** The user's referral code, created on first use. */
	@Transactional
	public String codeOf(UUID userId) {
		for (int attempt = 0; attempt < 5; attempt++) {
			Optional<String> existing = existingCode(userId);
			if (existing.isPresent()) {
				return existing.get();
			}
			// A conflict on the user (concurrent first use) or on the random code (taken by someone else)
			// inserts nothing instead of aborting the transaction; the next pass tells them apart.
			jdbc.update("""
					INSERT INTO referral_codes (user_id, code, created_at) VALUES (?, ?, ?)
					ON CONFLICT DO NOTHING
					""", userId, newCode(), now());
		}
		return existingCode(userId).orElseThrow(() -> new IllegalStateException("Could not allocate a referral code"));
	}

	// ------------------------------------------------------------------------ hierarchy

	/** Ancestors of an account, nearest first, at most four. */
	@Transactional(readOnly = true)
	public List<Upline> uplines(UUID userId) {
		return jdbc.query(UPLINES, (rs, i) -> new Upline(rs.getObject("referrer_id", UUID.class), rs.getInt("depth")),
				userId);
	}

	/** Whether an account currently earns commissions: active and identity verified. */
	@Transactional(readOnly = true)
	public Optional<String> ineligibility(UUID userId) {
		if (accounts.getAccount(userId).status() != UserStatus.ACTIVE) {
			return Optional.of("Your account is not active");
		}
		if (profiles.kycStatusOf(userId) != KycStatus.APPROVED) {
			return Optional.of("Verify your identity to earn referral commissions");
		}
		return Optional.empty();
	}

	@Transactional
	public ReferralOverview overview(UUID userId) {
		String code = codeOf(userId);
		String referredBy = uplines(userId).stream().findFirst()
			.map(u -> displayName(accounts.getAccount(u.userId())))
			.orElse(null);
		long[] counts = downlineSize(userId);
		ReferralRateVersion inForce = rates.inForce();
		List<ReferralOverview.Level> levels = new ArrayList<>();
		for (int level = 1; level <= LEVELS; level++) {
			levels.add(new ReferralOverview.Level(level, inForce.percentFor(level), counts[level - 1]));
		}
		Optional<String> ineligible = ineligibility(userId);
		return new ReferralOverview(code, referredBy, ineligible.isEmpty(), ineligible.orElse(null), levels,
				totals(earnings.totalsBySource(userId)), inForce.getEffectiveFrom());
	}

	@Transactional(readOnly = true)
	public Downline downline(UUID userId) {
		LoadedDownline loaded = load(userId);
		return new Downline(loaded.members(), loaded.truncated());
	}

	/**
	 * One member of the user's downline, by its id in downline views. Ids are positions in join order, which are stable
	 * while the hierarchy is: new members always join last, though a super administrator moving members renumbers them.
	 */
	@Transactional(readOnly = true)
	public DownlineMember member(UUID userId, String memberId) {
		LoadedDownline loaded = load(userId);
		return loaded.members().get(memberIndex(memberId, loaded.members().size()));
	}

	/** The account behind a downline member id; for the member's commission ledger. */
	@Transactional(readOnly = true)
	UUID memberUserId(UUID userId, String memberId) {
		LoadedDownline loaded = load(userId);
		return loaded.userIds().get(memberIndex(memberId, loaded.userIds().size()));
	}

	private static int memberIndex(String memberId, int members) {
		Matcher m = MEMBER_ID.matcher(memberId == null ? "" : memberId);
		int index = m.matches() ? Integer.parseInt(m.group(1)) - 1 : -1;
		if (index < 0 || index >= members) {
			throw new ResourceNotFoundException("Referral member", memberId);
		}
		return index;
	}

	private record LoadedDownline(List<DownlineMember> members, List<UUID> userIds, boolean truncated) {
	}

	private LoadedDownline load(UUID userId) {
		List<UUID[]> rows = new ArrayList<>();
		List<Integer> depths = new ArrayList<>();
		List<OffsetDateTime> joined = new ArrayList<>();
		jdbc.query(DOWNLINE + " SELECT user_id, referrer_id, depth, created_at FROM down ORDER BY created_at, user_id LIMIT ?",
				rs -> {
					rows.add(new UUID[] { rs.getObject("user_id", UUID.class), rs.getObject("referrer_id", UUID.class) });
					depths.add(rs.getInt("depth"));
					joined.add(rs.getObject("created_at", OffsetDateTime.class));
				}, userId, DOWNLINE_LIMIT + 1);
		boolean truncated = rows.size() > DOWNLINE_LIMIT;
		int size = Math.min(rows.size(), DOWNLINE_LIMIT);

		Map<UUID, UserAccount> people = accounts.getAccounts(rows.stream().limit(size).map(r -> r[0]).toList());
		Map<UUID, List<Money>> earned = new HashMap<>();
		for (ReferralTotal t : earnings.totalsBySource(userId)) {
			earned.computeIfAbsent(t.sourceUserId(), k -> new ArrayList<>())
				.add(Money.of(t.amount(), Currency.getInstance(t.currency())));
		}
		// Opaque node ids, so the response does not expose account ids of the downline.
		Map<UUID, String> nodeIds = new HashMap<>();
		for (int i = 0; i < size; i++) {
			nodeIds.put(rows.get(i)[0], "m" + (i + 1));
		}
		List<DownlineMember> members = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			UUID member = rows.get(i)[0];
			UUID parent = rows.get(i)[1];
			members.add(new DownlineMember(nodeIds.get(member), parent.equals(userId) ? null : nodeIds.get(parent),
					depths.get(i), displayName(people.get(member)), joined.get(i).toInstant(),
					earned.getOrDefault(member, List.of()).stream().map(MoneyResponse::from).toList()));
		}
		return new LoadedDownline(members, rows.stream().limit(size).map(r -> r[0]).toList(), truncated);
	}

	@Transactional(readOnly = true)
	public AdminReferralView adminView(UUID userId) {
		accounts.getAccount(userId);
		List<Upline> chain = uplines(userId);
		Map<UUID, UserAccount> people = accounts.getAccounts(chain.stream().map(Upline::userId).toList());
		List<AdminReferralView.Ancestor> ancestors = chain.stream()
			.map(u -> {
				UserAccount a = people.get(u.userId());
				return new AdminReferralView.Ancestor(u.level(), u.userId(), a.firstName() + " " + a.lastName(), a.email());
			})
			.toList();
		List<Long> sizes = new ArrayList<>();
		for (long count : downlineSize(userId)) {
			sizes.add(count);
		}
		return new AdminReferralView(existingCode(userId).orElse(null), ancestors, sizes,
				totals(earnings.totalsBySource(userId)));
	}

	/** The investor's whole downline, every level: shown to staff with the user, and arranged by super administrators. */
	@Transactional(readOnly = true)
	public AdminDownline adminDownline(UUID userId) {
		UserAccount investor = accounts.getAccount(userId);
		UUID investorReferrer = referrerOf(userId).orElse(null);
		String referrerName = investorReferrer == null ? null : fullName(accounts.getAccount(investorReferrer));
		// By level, so a truncated list keeps whole upper levels and every member's referrer comes before them.
		List<AdminDownlineRow> rows = jdbc.query("""
				WITH RECURSIVE down (user_id, referrer_id, depth, created_at) AS (
				    SELECT user_id, referrer_id, 1, created_at FROM referrals WHERE referrer_id = ?
				    UNION ALL
				    SELECT r.user_id, r.referrer_id, down.depth + 1, r.created_at
				    FROM referrals r JOIN down ON r.referrer_id = down.user_id
				    WHERE down.depth < ?)
				SELECT user_id, referrer_id, depth, created_at FROM down ORDER BY depth, created_at, user_id LIMIT ?""",
				(rs, i) -> new AdminDownlineRow(rs.getObject("user_id", UUID.class), rs.getObject("referrer_id", UUID.class),
						rs.getInt("depth"), rs.getObject("created_at", OffsetDateTime.class).toInstant()),
				userId, MAX_DEPTH, DOWNLINE_LIMIT + 1);
		boolean truncated = rows.size() > DOWNLINE_LIMIT;
		List<AdminDownlineRow> listed = rows.subList(0, Math.min(rows.size(), DOWNLINE_LIMIT));
		Map<UUID, UserAccount> people = accounts.getAccounts(listed.stream().map(AdminDownlineRow::userId).toList());
		List<AdminDownline.Member> members = listed.stream()
			.map(r -> {
				UserAccount a = people.get(r.userId());
				return new AdminDownline.Member(r.userId(), r.referrerId(), r.depth(), fullName(a), a.email(),
						a.status().name(), r.joinedAt());
			})
			.toList();
		return new AdminDownline(new AdminDownline.Investor(userId, fullName(investor), investor.email(),
				investor.status().name(), investorReferrer, referrerName), members, truncated);
	}

	private static String fullName(UserAccount account) {
		return account.firstName() + " " + account.lastName();
	}

	private Optional<UUID> referrerOf(UUID userId) {
		return jdbc.query("SELECT referrer_id FROM referrals WHERE user_id = ?",
				(rs, i) -> rs.getObject("referrer_id", UUID.class), userId).stream().findFirst();
	}

	private record AdminDownlineRow(UUID userId, UUID referrerId, int depth, Instant joinedAt) {
	}

	/**
	 * Moves a member, with their whole downline, to another referrer; with no referrer, takes them out of the downline
	 * they are in, so nobody refers them. Someone nobody referred can be placed under a referrer the same way.
	 * Commission from then on follows the new hierarchy; commission already paid stays with whoever it was paid to.
	 * The move is kept in referral_moves and the audit log.
	 */
	@Transactional
	public void move(UUID actorId, UUID memberId, UUID referrerId, String reason) {
		jdbc.execute("SELECT pg_advisory_xact_lock(" + MOVE_LOCK + ")");
		UserAccount member = accounts.getAccount(memberId);
		UUID current = jdbc.query("SELECT referrer_id FROM referrals WHERE user_id = ? FOR UPDATE",
				(rs, i) -> rs.getObject("referrer_id", UUID.class), memberId).stream().findFirst().orElse(null);
		String why = reason.strip();
		if (referrerId == null) {
			if (current == null) {
				throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
						member.firstName() + " is not in anyone's downline");
			}
			jdbc.update("DELETE FROM referrals WHERE user_id = ?", memberId);
			recordMove(actorId, memberId, current, null, why);
			return;
		}
		UserAccount referrer = accounts.getAccount(referrerId);
		if (referrerId.equals(memberId)) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "A member cannot be their own referrer");
		}
		if (referrerId.equals(current)) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					member.firstName() + " is already referred by " + referrer.firstName());
		}
		// Below themselves would make a cycle: the new referrer must not be in the member's downline.
		Boolean below = jdbc.queryForObject("""
				WITH RECURSIVE up (user_id, referrer_id, depth) AS (
				    SELECT user_id, referrer_id, 1 FROM referrals WHERE user_id = ?
				    UNION ALL
				    SELECT r.user_id, r.referrer_id, up.depth + 1
				    FROM referrals r JOIN up ON r.user_id = up.referrer_id
				    WHERE up.depth < ?)
				SELECT exists (SELECT 1 FROM up WHERE referrer_id = ?)""", Boolean.class, referrerId, MAX_DEPTH, memberId);
		if (Boolean.TRUE.equals(below)) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					referrer.firstName() + " is in " + member.firstName() + "'s downline; a member cannot move below themselves");
		}
		if (current == null) {
			// Placed for the first time: the row records the new referrer's code, as if entered at sign-up.
			jdbc.update("INSERT INTO referrals (user_id, referrer_id, code, created_at) VALUES (?, ?, ?, ?)", memberId,
					referrerId, codeOf(referrerId), now());
		} else {
			jdbc.update("UPDATE referrals SET referrer_id = ? WHERE user_id = ?", referrerId, memberId);
		}
		recordMove(actorId, memberId, current, referrerId, why);
	}

	/** One move in referral_moves and the audit log; a {@code null} referrer is nobody. */
	private void recordMove(UUID actorId, UUID memberId, UUID from, UUID to, String reason) {
		jdbc.update("""
				INSERT INTO referral_moves (id, user_id, from_referrer_id, to_referrer_id, moved_by, reason, moved_at)
				VALUES (?, ?, ?, ?, ?, ?, ?)""", UUID.randomUUID(), memberId, from, to, actorId, reason, now());
		audit.record(AuditRecord.of(actorId, AuditAction.REFERRAL_MOVED, ENTITY, memberId)
			.withOldValue(Map.of("referrerId", from == null ? "none" : from.toString()))
			.withNewValue(Map.of("referrerId", to == null ? "none" : to.toString(), "reason", reason)));
	}

	// ----------------------------------------------------------------------------- internal

	/** Members of the downline per level, 1-4. */
	private long[] downlineSize(UUID userId) {
		long[] counts = new long[LEVELS];
		jdbc.query(DOWNLINE + " SELECT depth, count(*) AS members FROM down GROUP BY depth",
				rs -> {
					counts[rs.getInt("depth") - 1] = rs.getLong("members");
				}, userId);
		return counts;
	}

	private Optional<String> existingCode(UUID userId) {
		return jdbc.query("SELECT code FROM referral_codes WHERE user_id = ?", (rs, i) -> rs.getString("code"), userId)
			.stream().findFirst();
	}

	private String newCode() {
		char[] code = new char[CODE_LENGTH];
		for (int i = 0; i < CODE_LENGTH; i++) {
			code[i] = ALPHABET[random.nextInt(ALPHABET.length)];
		}
		return new String(code);
	}

	/** "Ada L." - enough to recognise a referral without disclosing their full name. */
	static String displayName(UserAccount account) {
		if (account == null) {
			return "Member";
		}
		String last = account.lastName() == null || account.lastName().isBlank() ? ""
				: " " + account.lastName().strip().charAt(0) + ".";
		return account.firstName().strip() + last;
	}

	static List<MoneyResponse> totals(List<ReferralTotal> rows) {
		Map<String, Money> byCurrency = new TreeMap<>();
		for (ReferralTotal t : rows) {
			byCurrency.merge(t.currency(), Money.of(t.amount(), Currency.getInstance(t.currency())), Money::plus);
		}
		return byCurrency.values().stream().map(MoneyResponse::from).toList();
	}

	private OffsetDateTime now() {
		return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
	}

}

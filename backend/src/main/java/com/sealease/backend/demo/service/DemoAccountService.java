package com.sealease.backend.demo.service;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyProperties;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.common.time.DisplayTimeZone;
import com.sealease.backend.container.entity.ContainerType;
import com.sealease.backend.demo.dto.DemoAccountRequest;
import com.sealease.backend.demo.dto.DemoAccountSummary;
import com.sealease.backend.demo.dto.DemoAccountView;
import com.sealease.backend.demo.dto.DemoFrameRequest;
import com.sealease.backend.demo.dto.DemoMemberRequest;
import com.sealease.backend.demo.dto.DemoOrderRequest;
import com.sealease.backend.demo.service.DemoBook.Kind;
import com.sealease.backend.investment.dto.ProductResponse;
import com.sealease.backend.investment.entity.ProductTerms;
import com.sealease.backend.investment.service.ProductService;
import com.sealease.backend.investment.service.RentRateService;
import com.sealease.backend.referral.service.ReferralRateService;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.entity.UserCodes;
import com.sealease.backend.user.service.UserAccountService;

/**
 * Demo accounts, kept by super administrators: an investor marked as a demo account gets dummy orders and a dummy
 * downline over a time frame, and their own pages show the figures {@link DemoBook} works out from them. None of it
 * is in the ledger, and demo accounts are left out of real withdrawal runs.
 */
@Service
public class DemoAccountService {

	private static final String ENTITY = "DEMO_ACCOUNT";

	/** A time frame of at most ten years. */
	private static final int MAX_FRAME_YEARS = 10;

	private final JdbcTemplate jdbc;
	private final UserAccountService accounts;
	private final ReferralRateService referralRates;
	private final RentRateService rentRates;
	private final ProductService products;
	private final MoneyProperties money;
	private final DisplayTimeZone zone;
	private final AuditService audit;
	private final Clock clock;

	public DemoAccountService(JdbcTemplate jdbc, UserAccountService accounts, ReferralRateService referralRates,
			RentRateService rentRates, ProductService products, MoneyProperties money, DisplayTimeZone zone,
			AuditService audit, Clock clock) {
		this.jdbc = jdbc;
		this.accounts = accounts;
		this.referralRates = referralRates;
		this.rentRates = rentRates;
		this.products = products;
		this.money = money;
		this.zone = zone;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public boolean isDemo(UUID userId) {
		return Boolean.TRUE.equals(
				jdbc.queryForObject("SELECT exists (SELECT 1 FROM demo_accounts WHERE user_id = ?)", Boolean.class, userId));
	}

	@Transactional(readOnly = true)
	public List<DemoAccountSummary> list() {
		return jdbc.query("""
				SELECT a.user_id, u.user_number, u.first_name, u.last_name, u.email, a.starts_on, a.ends_on,
				       (SELECT count(*) FROM demo_orders o WHERE o.account_id = a.user_id) AS orders,
				       (SELECT count(*) FROM demo_members m WHERE m.account_id = a.user_id) AS members
				FROM demo_accounts a JOIN users u ON u.id = a.user_id
				ORDER BY u.first_name, u.last_name""",
				(rs, i) -> new DemoAccountSummary(rs.getObject("user_id", UUID.class), UserCodes.format(rs.getLong("user_number")),
						(rs.getString("first_name") + " " + rs.getString("last_name")).strip(), rs.getString("email"),
						rs.getObject("starts_on", LocalDate.class), rs.getObject("ends_on", LocalDate.class),
						rs.getLong("orders"), rs.getLong("members")));
	}

	/** The account's figures as of today. */
	@Transactional(readOnly = true)
	public DemoBook book(UUID userId) {
		LocalDate[] frame = frame(userId);
		Map<UUID, DemoBook.Member> members = members(userId);
		List<DemoBook.Order> orders = jdbc.query("""
				SELECT id, member_id, order_number, product_id, plan_code, plan_title, container_type, containers,
				       price_per_container, currency, rent_percent, tenure_months, starts_on
				FROM demo_orders WHERE account_id = ? ORDER BY starts_on, created_at""",
				(rs, i) -> new DemoBook.Order(rs.getObject("id", UUID.class), rs.getObject("member_id", UUID.class),
						rs.getString("order_number"), rs.getObject("product_id", UUID.class), rs.getString("plan_code"),
						rs.getString("plan_title"),
						ContainerType.valueOf(rs.getString("container_type")), rs.getInt("containers"),
						Money.of(rs.getBigDecimal("price_per_container"), Currency.getInstance(rs.getString("currency"))),
						rs.getBigDecimal("rent_percent"), rs.getInt("tenure_months"),
						rs.getObject("starts_on", LocalDate.class)),
				userId);
		return new DemoBook(frame[0], frame[1], today(), money.defaultCurrency(), orders,
				List.copyOf(members.values()), referralRates.inForce().percents());
	}

	@Transactional(readOnly = true)
	public DemoAccountView view(UUID userId) {
		UserAccount user = accounts.getAccount(userId);
		DemoBook book = book(userId);
		Map<UUID, String> names = new HashMap<>();
		book.members().forEach(m -> names.put(m.id(), m.name()));
		List<DemoAccountView.Order> orders = new ArrayList<>();
		for (DemoBook.Order o : book.ownOrders()) {
			orders.add(order(o, null, book.rentCredited(o)));
		}
		for (DemoBook.Order o : book.downlineOrders()) {
			orders.add(order(o, names.get(o.memberId()), null));
		}
		List<DemoAccountView.Member> members = book.members().stream()
			.map(m -> new DemoAccountView.Member(m.id(), m.parentId(), m.name(), m.joinedOn(), m.level(),
					MoneyResponse.from(book.commissionFrom(m.id()))))
			.toList();
		List<DemoAccountView.Month> months = book.months().stream()
			.map(m -> new DemoAccountView.Month(m.month().toString(), MoneyResponse.from(m.rentPaid()),
					MoneyResponse.from(m.rentScheduled()), MoneyResponse.from(m.commissionTotal()),
					MoneyResponse.from(m.commissionScheduled().stream().reduce(Money::plus).orElseThrow())))
			.toList();
		List<DemoAccountView.Payout> payouts = book.payouts().stream()
			.map(p -> new DemoAccountView.Payout(p.kind().name(), p.earnedIn().toString(), p.paidOn(),
					MoneyResponse.from(p.amount())))
			.toList();
		DemoAccountView.Totals totals = new DemoAccountView.Totals(MoneyResponse.from(book.rentPaid()),
				MoneyResponse.from(book.commissionPaid()), MoneyResponse.from(book.withdrawn(Kind.RENT)),
				MoneyResponse.from(book.withdrawn(Kind.REFERRAL)), MoneyResponse.from(book.balance(Kind.RENT)),
				MoneyResponse.from(book.balance(Kind.REFERRAL)));
		return new DemoAccountView(userId, user.userCode(), name(user), user.email(), book.startsOn(), book.endsOn(),
				book.today(), book.creditedUntil(), book.rates(), orders, members, months, payouts, totals, plans(),
				rentRates.get().tiers());
	}

	// ----------------------------------------------------------------------------- changes

	@Transactional
	public DemoAccountView create(UUID actorId, DemoAccountRequest request) {
		UserAccount user = accounts.getAccount(request.userId());
		if (!isInvestor(user.id())) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Only an investor account can be a demo account");
		}
		if (isDemo(user.id())) {
			throw new BusinessException(ErrorCode.CONFLICT, name(user) + " is already a demo account");
		}
		checkFrame(request.startsOn(), request.endsOn());
		Timestamp now = Timestamp.from(clock.instant());
		jdbc.update("""
				INSERT INTO demo_accounts (user_id, starts_on, ends_on, created_by, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?)""", user.id(), Date.valueOf(request.startsOn()), Date.valueOf(request.endsOn()),
				actorId, now, now);
		record(actorId, user.id(), "marked", Map.of("startsOn", request.startsOn().toString(),
				"endsOn", request.endsOn().toString()));
		return view(user.id());
	}

	/** Every dummy order and member must still fall inside the new time frame. */
	@Transactional
	public DemoAccountView changeFrame(UUID actorId, UUID userId, DemoFrameRequest request) {
		lock(userId);
		checkFrame(request.startsOn(), request.endsOn());
		Integer outside = jdbc.queryForObject("""
				SELECT (SELECT count(*) FROM demo_orders WHERE account_id = ? AND (starts_on < ? OR starts_on > ?))
				     + (SELECT count(*) FROM demo_members WHERE account_id = ? AND (joined_on < ? OR joined_on > ?))""",
				Integer.class, userId, Date.valueOf(request.startsOn()), Date.valueOf(request.endsOn()), userId,
				Date.valueOf(request.startsOn()), Date.valueOf(request.endsOn()));
		if (outside != null && outside > 0) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, outside
					+ " dummy order(s) or member(s) fall outside that time frame; change or remove them first");
		}
		jdbc.update("UPDATE demo_accounts SET starts_on = ?, ends_on = ?, updated_at = ? WHERE user_id = ?",
				Date.valueOf(request.startsOn()), Date.valueOf(request.endsOn()), Timestamp.from(clock.instant()), userId);
		record(actorId, userId, "time frame changed", Map.of("startsOn", request.startsOn().toString(),
				"endsOn", request.endsOn().toString()));
		return view(userId);
	}

	/** No longer a demo account: its dummy orders and downline are deleted, and its own pages show real figures. */
	@Transactional
	public void remove(UUID actorId, UUID userId) {
		lock(userId);
		jdbc.update("DELETE FROM demo_accounts WHERE user_id = ?", userId);
		record(actorId, userId, "unmarked", Map.of());
	}

	@Transactional
	public DemoAccountView addOrder(UUID actorId, UUID userId, DemoOrderRequest request) {
		LocalDate[] frame = lock(userId);
		inFrame(frame, request.startsOn(), "The lease start");
		if (request.memberId() != null) {
			DemoBook.Member member = members(userId).get(request.memberId());
			if (member == null) {
				throw new ResourceNotFoundException("Demo member", request.memberId());
			}
			if (request.startsOn().isBefore(member.joinedOn())) {
				throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
						"The lease cannot start before " + member.name() + " joined, on " + day(member.joinedOn()));
			}
		}
		ProductResponse plan = products.published().stream().filter(p -> p.id().equals(request.productId())).findFirst()
			.orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Choose an open or closed plan"));
		if (request.tenureYears() < plan.minTenureYears() || request.tenureYears() > plan.maxTenureYears()) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, plan.title() + " is leased for "
					+ plan.minTenureYears() + " to " + plan.maxTenureYears() + " years");
		}
		Currency currency = Currency.getInstance(plan.price().currency());
		BigDecimal price = new BigDecimal(plan.price().amount());
		Money total = Money.of(price, currency).times(BigDecimal.valueOf(request.containers()));
		// Rent is the rate of the band the order's total falls in, as for a real order.
		BigDecimal rent = rentRates.percentFor(total);
		if (rent == null && request.memberId() != null) {
			// A member's rent is not shown anywhere: their commission is on the investment.
			rent = BigDecimal.ZERO;
		}
		if (rent == null) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"An order of " + total.display() + " is outside every rent band; add containers");
		}
		Long number = jdbc.queryForObject("SELECT nextval('demo_order_number_seq')", Long.class);
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO demo_orders (id, account_id, member_id, order_number, product_id, plan_code, plan_title,
				                         container_type, containers, price_per_container, currency, rent_percent,
				                         tenure_months, starts_on, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", id, userId, request.memberId(), "ORD-" + number,
				plan.id(), plan.code(), plan.title(), plan.containerType().name(), request.containers(), price,
				currency.getCurrencyCode(), rent, ProductTerms.monthsOf(request.tenureYears()),
				Date.valueOf(request.startsOn()), Timestamp.from(clock.instant()));
		record(actorId, userId, "order added", Map.of("order", "ORD-" + number, "plan", plan.code(), "member",
				request.memberId() == null ? "own" : request.memberId().toString(), "amount", total.toString(),
				"rentPercent", rent.toPlainString(), "startsOn", request.startsOn().toString()));
		return view(userId);
	}

	@Transactional
	public DemoAccountView removeOrder(UUID actorId, UUID userId, UUID orderId) {
		lock(userId);
		if (jdbc.update("DELETE FROM demo_orders WHERE id = ? AND account_id = ?", orderId, userId) == 0) {
			throw new ResourceNotFoundException("Demo order", orderId);
		}
		record(actorId, userId, "order removed", Map.of("orderId", orderId.toString()));
		return view(userId);
	}

	@Transactional
	public DemoAccountView addMember(UUID actorId, UUID userId, DemoMemberRequest request) {
		LocalDate[] frame = lock(userId);
		inFrame(frame, request.joinedOn(), "The joining date");
		if (request.parentId() != null) {
			DemoBook.Member parent = members(userId).get(request.parentId());
			if (parent == null) {
				throw new ResourceNotFoundException("Demo member", request.parentId());
			}
			if (request.joinedOn().isBefore(parent.joinedOn())) {
				throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
						"A member cannot join before " + parent.name() + ", who referred them, joined on " + day(parent.joinedOn()));
			}
		}
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO demo_members (id, account_id, parent_id, name, joined_on, created_at)
				VALUES (?, ?, ?, ?, ?, ?)""", id, userId, request.parentId(), request.name().strip(),
				Date.valueOf(request.joinedOn()), Timestamp.from(clock.instant()));
		record(actorId, userId, "member added", Map.of("member", request.name().strip(), "parent",
				request.parentId() == null ? "investor" : request.parentId().toString()));
		return view(userId);
	}

	/** Removes the member with everyone below them and their orders. */
	@Transactional
	public DemoAccountView removeMember(UUID actorId, UUID userId, UUID memberId) {
		lock(userId);
		if (jdbc.update("DELETE FROM demo_members WHERE id = ? AND account_id = ?", memberId, userId) == 0) {
			throw new ResourceNotFoundException("Demo member", memberId);
		}
		record(actorId, userId, "member removed", Map.of("memberId", memberId.toString()));
		return view(userId);
	}

	// ----------------------------------------------------------------------------- internal

	public LocalDate today() {
		return LocalDate.now(clock.withZone(zone.zone()));
	}

	private LocalDate[] frame(UUID userId) {
		return jdbc.query("SELECT starts_on, ends_on FROM demo_accounts WHERE user_id = ?",
				(rs, i) -> new LocalDate[] { rs.getObject("starts_on", LocalDate.class), rs.getObject("ends_on", LocalDate.class) },
				userId).stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Demo account", userId));
	}

	/** Serialises changes to one account; its time frame. */
	private LocalDate[] lock(UUID userId) {
		return jdbc.query("SELECT starts_on, ends_on FROM demo_accounts WHERE user_id = ? FOR UPDATE",
				(rs, i) -> new LocalDate[] { rs.getObject("starts_on", LocalDate.class), rs.getObject("ends_on", LocalDate.class) },
				userId).stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Demo account", userId));
	}

	/** The dummy downline, each member with their level (1 = referred by the investor), in joining order. */
	private Map<UUID, DemoBook.Member> members(UUID userId) {
		record Row(UUID id, UUID parentId, String name, LocalDate joinedOn) {
		}
		List<Row> rows = jdbc.query(
				"SELECT id, parent_id, name, joined_on FROM demo_members WHERE account_id = ? ORDER BY joined_on, created_at",
				(rs, i) -> new Row(rs.getObject("id", UUID.class), rs.getObject("parent_id", UUID.class),
						rs.getString("name"), rs.getObject("joined_on", LocalDate.class)),
				userId);
		Map<UUID, Row> byId = new HashMap<>();
		rows.forEach(r -> byId.put(r.id(), r));
		Map<UUID, DemoBook.Member> members = new LinkedHashMap<>();
		for (Row row : rows) {
			int level = 1;
			for (Row up = byId.get(row.parentId()); up != null; up = byId.get(up.parentId())) {
				level++;
			}
			members.put(row.id(), new DemoBook.Member(row.id(), row.parentId(), row.name(), row.joinedOn(), level));
		}
		return members;
	}

	private DemoAccountView.Order order(DemoBook.Order o, String memberName, Money rentCredited) {
		return new DemoAccountView.Order(o.id(), o.memberId(), memberName, o.number(), o.productId(), o.planCode(),
				o.planTitle(), o.containerType(),
				o.containers(), MoneyResponse.from(o.pricePerContainer()), MoneyResponse.from(o.amount()), o.rentPercent(),
				MoneyResponse.from(o.monthlyRent()), o.tenureMonths(), o.startsOn(), o.endsOn(),
				rentCredited == null ? null : MoneyResponse.from(rentCredited));
	}

	private List<DemoAccountView.Plan> plans() {
		return products.published().stream()
			.map(p -> new DemoAccountView.Plan(p.id(), p.code(), p.title(), p.containerType(), p.price(),
					p.minTenureYears(), p.maxTenureYears(), p.status().name()))
			.toList();
	}

	private boolean isInvestor(UUID userId) {
		return Boolean.TRUE.equals(jdbc.queryForObject("""
				SELECT exists (SELECT 1 FROM user_roles ur
				               JOIN role_permissions rp ON rp.role_id = ur.role_id
				               JOIN permissions pm ON pm.id = rp.permission_id
				               WHERE ur.user_id = ? AND pm.code = 'INVESTOR_PORTAL')""", Boolean.class, userId));
	}

	private static void checkFrame(LocalDate startsOn, LocalDate endsOn) {
		if (endsOn.isBefore(startsOn)) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "The time frame must end on or after its start");
		}
		if (YearMonth.from(endsOn).isAfter(YearMonth.from(startsOn).plusYears(MAX_FRAME_YEARS))) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"The time frame can be at most " + MAX_FRAME_YEARS + " years");
		}
	}

	private static void inFrame(LocalDate[] frame, LocalDate day, String what) {
		if (day.isBefore(frame[0]) || day.isAfter(frame[1])) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					what + " must be within the time frame, " + day(frame[0]) + " to " + day(frame[1]));
		}
	}

	private void record(UUID actorId, UUID userId, String change, Map<String, String> details) {
		Map<String, String> value = new LinkedHashMap<>();
		value.put("change", change);
		value.putAll(details);
		audit.record(AuditRecord.of(actorId, AuditAction.DEMO_ACCOUNT_CHANGED, ENTITY, userId).withNewValue(value));
	}

	/** "1 Feb 2026", as dates are shown. */
	private static String day(LocalDate date) {
		return date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH));
	}

	private static String name(UserAccount user) {
		return (user.firstName() + " " + user.lastName()).strip();
	}

}

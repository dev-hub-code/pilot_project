package com.sealease.backend.dashboard;

import com.sealease.backend.permission.PermissionCode;
import com.sealease.backend.security.AuthenticatedUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The admin home page: each section appears only for those allowed to see the data behind it. */
@RestController
public class DashboardController {

	public record Metric(String label, long value, String href) {
	}

	public record Section(String key, String title, List<Metric> metrics) {
	}

	private static final String INVESTORS = """
			SELECT DISTINCT ur.user_id FROM user_roles ur
			         JOIN role_permissions rp ON rp.role_id = ur.role_id
			         JOIN permissions p ON p.id = rp.permission_id
			WHERE p.code = 'INVESTOR_PORTAL'""";

	private final JdbcTemplate jdbc;

	public DashboardController(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@GetMapping("/api/v1/admin/dashboard")
	@PreAuthorize("isAuthenticated()")
	public List<Section> dashboard(AuthenticatedUser viewer) {
		Set<String> held = viewer.permissions();
		if (held.contains(PermissionCode.INVESTOR_PORTAL.name())) {
			return List.of();
		}
		UUID me = viewer.userId();
		List<Section> sections = new ArrayList<>();
		add(sections, "platform", "Platform", java.util.Arrays.asList(
				when(held, PermissionCode.STAFF_ACCOUNT_VIEW, "Employees", "SELECT count(DISTINCT user_id) FROM user_roles WHERE user_id NOT IN (" + INVESTORS + ")", "/platform/staff"),
				when(held, PermissionCode.DEPARTMENT_MANAGE, "Active departments", "SELECT count(*) FROM departments WHERE active", "/platform/departments"),
				when(held, PermissionCode.ROLE_VIEW, "Roles", "SELECT count(*) FROM roles", "/platform/roles"),
				when(held, PermissionCode.APPROVAL_VIEW, "Approvals waiting", "SELECT count(*) FROM approval_requests WHERE status = 'PENDING'", "/platform/approvals")));
		add(sections, "investors", "Investor relations", java.util.Arrays.asList(
				when(held, PermissionCode.USER_VIEW, "Investors", "SELECT count(*) FROM (" + INVESTORS + ") i", "/platform/users"),
				when(held, PermissionCode.USER_VIEW, "New in 7 days", "SELECT count(*) FROM users WHERE created_at > now() - interval '7 days' AND id IN (" + INVESTORS + ")", "/platform/users")));
		add(sections, "investments", "Investment operations", java.util.Arrays.asList(
				when(held, PermissionCode.INVESTMENT_VIEW, "Open plans", "SELECT count(*) FROM investment_products WHERE status = 'OPEN'", "/platform/products"),
				when(held, PermissionCode.ORDER_VIEW, "Orders awaiting payment", "SELECT count(*) FROM orders WHERE status = 'PENDING_PAYMENT'", "/platform/orders"),
				when(held, PermissionCode.ORDER_VIEW, "Confirmed orders", "SELECT count(*) FROM orders WHERE status = 'CONFIRMED'", "/platform/orders")));
		add(sections, "finance", "Finance", java.util.Arrays.asList(
				when(held, PermissionCode.FINANCE_VIEW, "Pending payments", "SELECT count(*) FROM payments WHERE status = 'PENDING'", "/platform/payments"),
				when(held, PermissionCode.FINANCE_VIEW, "Confirmed payments", "SELECT count(*) FROM payments WHERE status = 'SUCCEEDED'", "/platform/payments"),
				when(held, PermissionCode.WITHDRAWAL_VIEW, "Pending withdrawals", "SELECT count(*) FROM withdrawals WHERE status = 'PENDING_APPROVAL'", "/platform/withdrawals")));
		add(sections, "compliance", "Compliance", java.util.Arrays.asList(
				when(held, PermissionCode.KYC_REVIEW, "Pending KYC", "SELECT count(*) FROM kyc_submissions WHERE status = 'PENDING'", "/platform/verification"),
				when(held, PermissionCode.KYC_REVIEW, "Rejected KYC", "SELECT count(*) FROM kyc_submissions WHERE status = 'REJECTED'", "/platform/verification"),
				when(held, PermissionCode.BANK_ACCOUNT_VERIFY, "Pending bank verification", "SELECT count(*) FROM bank_accounts WHERE status = 'PENDING_VERIFICATION'", "/platform/verification")));
		add(sections, "support", "Customer support", java.util.Arrays.asList(
				when(held, PermissionCode.SUPPORT_TICKET_VIEW, "Open tickets", "SELECT count(*) FROM support_tickets WHERE status = 'OPEN'", "/platform/support"),
				when(held, PermissionCode.SUPPORT_TICKET_VIEW, "Unassigned", "SELECT count(*) FROM support_tickets WHERE status = 'OPEN' AND assignee_id IS NULL", "/platform/support"),
				held.contains(PermissionCode.SUPPORT_TICKET_VIEW.name()) ? metric("Assigned to me", "/platform/support",
						"SELECT count(*) FROM support_tickets WHERE status = 'OPEN' AND assignee_id = ?", me) : null));
		add(sections, "sales", "Sales", java.util.Arrays.asList(
				when(held, PermissionCode.LEAD_VIEW, "New leads in 7 days", "SELECT count(*) FROM leads WHERE created_at > now() - interval '7 days'", "/platform/leads"),
				when(held, PermissionCode.LEAD_VIEW, "Won leads", "SELECT count(*) FROM leads WHERE stage = 'WON'", "/platform/leads"),
				held.contains(PermissionCode.LEAD_VIEW.name()) ? metric("Assigned to me", "/platform/leads",
						"SELECT count(*) FROM leads WHERE owner_id = ?", me) : null));
		add(sections, "partners", "Partner operations", java.util.Arrays.asList(
				when(held, PermissionCode.FINANCE_VIEW, "Referrals", "SELECT count(*) FROM referrals", "/platform/referrals"),
				when(held, PermissionCode.FINANCE_VIEW, "Commissions paid", "SELECT count(*) FROM referral_earnings", "/platform/referrals")));
		return sections;
	}

	private Metric when(Set<String> held, PermissionCode needed, String label, String sql, String href) {
		return held.contains(needed.name()) ? metric(label, href, sql) : null;
	}

	private Metric metric(String label, String href, String sql, Object... args) {
		Long value = jdbc.queryForObject(sql, Long.class, args);
		return new Metric(label, value == null ? 0 : value, href);
	}

	private static void add(List<Section> sections, String key, String title, List<Metric> metrics) {
		List<Metric> shown = metrics.stream().filter(java.util.Objects::nonNull).toList();
		if (!shown.isEmpty()) {
			sections.add(new Section(key, title, shown));
		}
	}

}

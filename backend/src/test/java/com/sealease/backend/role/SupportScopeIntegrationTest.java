package com.sealease.backend.role;

import com.sealease.backend.support.IntegrationTest;
import com.sealease.backend.support.InvestorFixtures;
import com.sealease.backend.support.OfferingFixtures;
import com.sealease.backend.support.TestApi;
import com.sealease.backend.support.TestApi.Account;
import com.sealease.backend.user.entity.UserCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Support finds an investor's orders and payments only by an exact search, and has no access to withdrawals. */
@IntegrationTest
class SupportScopeIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private TestApi api;
	private Account support;

	@BeforeEach
	void setUp() throws Exception {
		api = new TestApi(mvc, jdbc);
		support = api.staff("SUPPORT");
	}

	@Test
	void supportFindsOrdersAndPaymentsOnlyByAnExactSearch() throws Exception {
		InvestorFixtures investors = new InvestorFixtures(mvc, jdbc, api);
		Account investor = investors.approvedInvestor();
		String orderId = investors.paidOrder(investor, new OfferingFixtures(mvc, api.admin()).plan(), 1);
		String orderNumber = jdbc.queryForObject("SELECT order_number FROM orders WHERE id = ?::uuid", String.class, orderId);
		String deposit = jdbc.queryForObject("SELECT deposit_reference FROM payments WHERE order_id = ?::uuid", String.class, orderId);
		String code = UserCodes.format(jdbc.queryForObject("SELECT user_number FROM users WHERE id = ?", Long.class, investor.id()));

		// No browsing: without a search, nothing.
		fetch("/api/v1/admin/orders").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
		fetch("/api/v1/admin/payments").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
		fetch("/api/v1/admin/payments?status=SUCCEEDED").andExpect(jsonPath("$.totalElements").value(0));
		// Part of a number is not enough.
		fetch("/api/v1/admin/orders?q=" + orderNumber.substring(0, orderNumber.length() - 1))
			.andExpect(jsonPath("$.totalElements").value(0));

		// A whole order number, or the investor's ID or email, finds them.
		for (String q : new String[] { orderNumber, orderNumber.toLowerCase(), code, investor.email() }) {
			fetch("/api/v1/admin/orders?q=" + q).andExpect(jsonPath("$.content[0].id").value(orderId));
			fetch("/api/v1/admin/payments?q=" + q).andExpect(jsonPath("$.content[0].orderId").value(orderId));
		}
		// (Test deposit references are not unique across payments, so look for this one among the results.)
		fetch("/api/v1/admin/payments?q=" + deposit).andExpect(jsonPath("$.content[*].orderId", hasItem(orderId)));
		fetch("/api/v1/admin/orders/" + orderId).andExpect(status().isOk());

		// Admins still browse.
		mvc.perform(get("/api/v1/admin/orders").header(HttpHeaders.AUTHORIZATION, api.admin().bearer()))
			.andExpect(jsonPath("$.totalElements").value(greaterThan(0)));
	}

	@Test
	void withdrawalsAreForAdminsOnly() throws Exception {
		fetch("/api/v1/admin/withdrawals").andExpect(status().isForbidden());
		fetch("/api/v1/admin/withdrawals/" + UUID.randomUUID()).andExpect(status().isForbidden());
		fetch("/api/v1/admin/withdrawal-batches").andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/admin/withdrawals").header(HttpHeaders.AUTHORIZATION, api.staff("ADMIN").bearer()))
			.andExpect(status().isOk());
	}

	private ResultActions fetch(String path) throws Exception {
		return mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, support.bearer()));
	}

}

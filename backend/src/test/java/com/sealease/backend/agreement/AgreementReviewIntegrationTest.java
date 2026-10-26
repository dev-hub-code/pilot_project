package com.sealease.backend.agreement;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.support.IntegrationTest;
import com.sealease.backend.support.InvestorFixtures;
import com.sealease.backend.support.OfferingFixtures;
import com.sealease.backend.support.TestApi;
import com.sealease.backend.support.TestApi.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every agreement issued opens a review task; a report viewer takes it, comments on it and marks it done. */
@IntegrationTest
class AgreementReviewIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private TestApi api;
	private InvestorFixtures investors;
	private OfferingFixtures offerings;

	@BeforeEach
	void setUp() throws Exception {
		api = new TestApi(mvc, jdbc);
		investors = new InvestorFixtures(mvc, jdbc, api);
		offerings = new OfferingFixtures(mvc, api.admin());
	}

	@Test
	void aReportViewerTakesCommentsOnAndCompletesTheAgreementsReviewTask() throws Exception {
		Account investor = investors.approvedInvestor();
		String orderId = investors.invest(investor, offerings.plan(), 1);
		UUID agreement = jdbc.queryForObject("SELECT id FROM lease_agreements WHERE order_id = ?::uuid", UUID.class, orderId);
		UUID task = jdbc.queryForObject("SELECT id FROM agreement_review_tasks WHERE agreement_id = ?", UUID.class, agreement);

		// Report viewers (and super admins) review agreements; admins without the permission do not.
		Account reviewer = api.staff("REPORT_VIEWER");
		Account colleague = api.staff("REPORT_VIEWER");
		as(api.staff("ADMIN"), get("/api/v1/admin/agreement-reviews/{id}", task)).andExpect(status().isForbidden());

		String open = as(reviewer, get("/api/v1/admin/agreement-reviews").param("size", "100")).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<java.util.List<String>>read(open, "$.content[*].id")).contains(task.toString());
		as(reviewer, get("/api/v1/admin/agreement-reviews/{id}", task)).andExpect(status().isOk())
			.andExpect(jsonPath("$.task.status").value("OPEN"))
			.andExpect(jsonPath("$.task.assignee").doesNotExist())
			.andExpect(jsonPath("$.task.agreement.id").value(agreement.toString()))
			.andExpect(jsonPath("$.task.investor.id").value(investor.id().toString()))
			.andExpect(jsonPath("$.wording").isNotEmpty());
		as(reviewer, get("/api/v1/admin/agreement-reviews/{id}/pdf", task)).andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_PDF));

		// Only the assignee completes it; anyone reviewing may comment.
		complete(reviewer, task, null).andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.message").value(containsString("Assign this task to yourself")));
		as(reviewer, post("/api/v1/admin/agreement-reviews/{id}/assign", task)).andExpect(status().isOk())
			.andExpect(jsonPath("$.task.assignee.id").value(reviewer.id().toString()));
		as(colleague, post("/api/v1/admin/agreement-reviews/{id}/assign", task)).andExpect(status().isConflict());
		complete(colleague, task, null).andExpect(status().isUnprocessableEntity());
		as(colleague, post("/api/v1/admin/agreement-reviews/{id}/comments", task).contentType(MediaType.APPLICATION_JSON)
				.content("{\"body\":\"Check the container location\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.comments.length()").value(1));
		as(reviewer, post("/api/v1/admin/agreement-reviews/{id}/comments", task).contentType(MediaType.APPLICATION_JSON)
				.content("{\"body\":\"  \"}"))
			.andExpect(status().isBadRequest());

		complete(reviewer, task, "Location confirmed; all in order.").andExpect(status().isOk())
			.andExpect(jsonPath("$.task.status").value("DONE"))
			.andExpect(jsonPath("$.task.completedBy.id").value(reviewer.id().toString()))
			.andExpect(jsonPath("$.task.comments").value(2))
			.andExpect(jsonPath("$.comments[1].body").value("Location confirmed; all in order."))
			.andExpect(jsonPath("$.comments[0].authorName").isNotEmpty());
		complete(reviewer, task, null).andExpect(status().isConflict());
		String done = as(reviewer, get("/api/v1/admin/agreement-reviews").param("status", "DONE").param("mine", "true"))
			.andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<java.util.List<String>>read(done, "$.content[*].id")).containsExactly(task.toString());
		assertThat(api.auditCount("AGREEMENT_REVIEW_COMPLETED", task)).isEqualTo(1);
	}

	private ResultActions complete(Account reviewer, UUID task, String comment) throws Exception {
		return as(reviewer, post("/api/v1/admin/agreement-reviews/{id}/complete", task).contentType(MediaType.APPLICATION_JSON)
			.content(comment == null ? "{}" : "{\"comment\":\"" + comment + "\"}"));
	}

	private ResultActions as(Account account,
			org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, account.bearer()));
	}

}

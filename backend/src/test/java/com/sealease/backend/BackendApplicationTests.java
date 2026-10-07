package com.sealease.backend;

import com.sealease.backend.kafka.KafkaTopics;
import com.sealease.backend.support.IntegrationTest;
import org.apache.kafka.clients.admin.AdminClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the full application against real PostgreSQL and Kafka. Skipped when Docker is not
 * available so that the unit and slice tests still run on any machine.
 */
@IntegrationTest
class BackendApplicationTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private KafkaAdmin kafkaAdmin;

	@Test
	void flywayAppliedBaseline() {
		Integer applied = jdbc.queryForObject(
				"SELECT count(*) FROM flyway_schema_history WHERE version IN ('1', '2', '3') AND success", Integer.class);
		assertThat(applied).isEqualTo(3);
	}

	@Test
	void forbidMutationTriggerBlocksUpdatesAndDeletes() {
		jdbc.execute("CREATE TEMP TABLE ledger_probe (id INT PRIMARY KEY, amount NUMERIC(19,4) NOT NULL)");
		jdbc.execute("CREATE TRIGGER ledger_probe_immutable BEFORE UPDATE OR DELETE ON ledger_probe "
				+ "FOR EACH ROW EXECUTE FUNCTION forbid_mutation()");
		jdbc.update("INSERT INTO ledger_probe VALUES (1, 100.0000)");

		assertThatThrownBy(() -> jdbc.update("UPDATE ledger_probe SET amount = 1 WHERE id = 1"))
			.hasMessageContaining("append-only");
		assertThatThrownBy(() -> jdbc.update("DELETE FROM ledger_probe WHERE id = 1"))
			.hasMessageContaining("append-only");
		assertThat(jdbc.queryForObject("SELECT amount FROM ledger_probe WHERE id = 1", String.class))
			.isEqualTo("100.0000");
	}

	@Test
	void domainTopicsAndDeadLetterTopicsExist() throws Exception {
		try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
			Set<String> topics = admin.listTopics().names().get();
			assertThat(topics).contains(KafkaTopics.WITHDRAWAL_REQUESTED,
					KafkaTopics.deadLetterOf(KafkaTopics.WITHDRAWAL_REQUESTED));
			assertThat(topics).containsAll(KafkaTopics.ALL);
		}
	}

	@Test
	void healthProbesArePublic() throws Exception {
		mvc.perform(get("/actuator/health/liveness"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"));
		mvc.perform(get("/actuator/health/readiness"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void metricsEndpointsRequireAuthentication() throws Exception {
		mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
		mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
	}

}

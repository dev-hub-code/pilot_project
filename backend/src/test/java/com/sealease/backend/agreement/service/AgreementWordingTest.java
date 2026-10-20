package com.sealease.backend.agreement.service;

import com.sealease.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Placeholders in agreement wording, and rendering the wording as a PDF. */
class AgreementWordingTest {

	@Test
	void placeholdersAreFilledInOnce() {
		String filled = Placeholder.fill("Dear {{investorName}}, container {{ containerNumber }}.",
				Map.of(Placeholder.INVESTOR_NAME, "{{containerNumber}} $1 Rao", Placeholder.CONTAINER_NUMBER, "SLSU1234565"));
		// A value is never itself treated as a placeholder or a regex group.
		assertThat(filled).isEqualTo("Dear {{containerNumber}} $1 Rao, container SLSU1234565.");
	}

	@Test
	void unknownPlaceholdersAreRefusedByName() {
		Placeholder.requireKnown("{{investorName}} leases {{containerNumber}}");
		assertThatThrownBy(() -> Placeholder.requireKnown("{{investorNmae}} and {{rent}} and {{investorName}}"))
			.isInstanceOf(BusinessException.class)
			.hasMessageContaining("{{investorNmae}}, {{rent}}")
			.hasMessageNotContaining("{{investorName}}");
	}

	@Test
	void paragraphsAreSplitOnBlankLinesAndHeadingsMarked() {
		List<Map<String, ?>> rows = AgreementRenderer.paragraphs(
				"Intro line one\nline two\r\n\r\n# 1. Rent\n\n  \n\nPaid monthly.\n\n# 2. End\nThe container returns.");
		assertThat(rows).containsExactly(
				Map.of("text", "Intro line one\nline two", "heading", false),
				Map.of("text", "1. Rent", "heading", true),
				Map.of("text", "Paid monthly.", "heading", false),
				Map.of("text", "2. End", "heading", true),
				Map.of("text", "The container returns.", "heading", false));
	}

	@Test
	void theShippedWordingUsesOnlyKnownPlaceholdersAndRendersAsPdf() throws Exception {
		String migration = Files.readString(Path.of("src/main/resources/db/migration/V1__schema.sql"),
				StandardCharsets.UTF_8);
		String body = migration.substring(migration.indexOf("$body$") + 6, migration.lastIndexOf("$body$"));
		Placeholder.requireKnown(body);
		String filled = Placeholder.fill(body, Placeholder.examples());
		assertThat(filled).doesNotContain("{{").contains("SLSU1234565", "₹3,750.00");

		byte[] pdf = new AgreementRenderer().pdf("Container Lease Agreement", "AGR-2026-000042 · issued 17 October 2026",
				filled, "Issued electronically by SeaLease");
		assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
	}

}

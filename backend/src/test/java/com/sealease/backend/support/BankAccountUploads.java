package com.sealease.backend.support;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** Requests that add a bank account (or change one), with the bank statement every account must come with. */
public final class BankAccountUploads {

	/** A minimal file the document store accepts as a PDF. */
	public static final byte[] STATEMENT_PDF = "%PDF-1.4 bank statement".getBytes(StandardCharsets.US_ASCII);

	private BankAccountUploads() {
	}

	/** {@code POST /users/me/bank-accounts} with the account's JSON and a bank statement. */
	public static MockMultipartHttpServletRequestBuilder addAccount(String accountJson) {
		return withProof(multipart("/api/v1/users/me/bank-accounts"), accountJson);
	}

	private static MockMultipartHttpServletRequestBuilder withProof(MockMultipartHttpServletRequestBuilder request,
			String accountJson) {
		request.file(new MockMultipartFile("account", "", MediaType.APPLICATION_JSON_VALUE,
				accountJson.getBytes(StandardCharsets.UTF_8)));
		request.file(new MockMultipartFile("proof", "statement.pdf", MediaType.APPLICATION_PDF_VALUE, STATEMENT_PDF));
		request.param("proofType", "BANK_STATEMENT");
		return request;
	}

}

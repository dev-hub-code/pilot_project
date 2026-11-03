package com.sealease.backend.support;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** Requests that add a bank account (or change one), with the bank statement every account must come with. */
public final class BankAccountUploads {

	/** A minimal file the document store accepts as a PDF. */
	/** A PNG (only JPG and PNG images are accepted): its signature, then a marker. */
	public static final byte[] STATEMENT_IMAGE = "\u0089PNG\r\n\u001a\nbank statement".getBytes(StandardCharsets.ISO_8859_1);

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
		request.file(new MockMultipartFile("proof", "statement.png", MediaType.IMAGE_PNG_VALUE, STATEMENT_IMAGE));
		request.param("proofType", "BANK_STATEMENT");
		return request;
	}

}

package com.sealease.backend.bankaccount.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** The IFSC lookup against a stand-in for the online directory, so it runs without the network. */
class IfscDirectoryTest {

	private HttpServer directory;
	private final AtomicInteger requests = new AtomicInteger();

	@BeforeEach
	void startDirectory() throws IOException {
		directory = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		directory.createContext("/", exchange -> {
			requests.incrementAndGet();
			String path = exchange.getRequestURI().getPath();
			byte[] body;
			int status;
			if (path.equals("/HDFC0000001")) {
				status = 200;
				body = """
						{"BANK":"HDFC Bank","BRANCH":"TULSIANI CHMBRS - NARIMAN PT","CITY":"GREATER MUMBAI",
						 "STATE":"MAHARASHTRA","IFSC":"HDFC0000001","MICR":"400240003"}""".getBytes(StandardCharsets.UTF_8);
			} else if (path.equals("/DOWN0000001")) {
				status = 503;
				body = "\"Unavailable\"".getBytes(StandardCharsets.UTF_8);
			} else {
				status = 404;
				body = "\"Not Found\"".getBytes(StandardCharsets.UTF_8);
			}
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		directory.start();
	}

	@AfterEach
	void stopDirectory() {
		directory.stop(0);
	}

	private IfscDirectory lookups(boolean enabled) {
		return new IfscDirectory(new IfscProperties(enabled,
				URI.create("http://127.0.0.1:" + directory.getAddress().getPort()), Duration.ofSeconds(2)));
	}

	@Test
	void findsBranchesRefusesUnknownCodesAndCachesAnswers() {
		IfscDirectory ifsc = lookups(true);

		IfscDirectory.Lookup found = ifsc.lookup(" hdfc0000001 ");
		assertThat(found.status()).isEqualTo(IfscDirectory.Status.FOUND);
		assertThat(found.branch().bank()).isEqualTo("HDFC Bank");
		assertThat(found.branch().place()).isEqualTo("TULSIANI CHMBRS - NARIMAN PT, GREATER MUMBAI, MAHARASHTRA");

		assertThat(ifsc.lookup("ABCD0123456").status()).isEqualTo(IfscDirectory.Status.NOT_FOUND);
		// Not shaped like an IFSC: refused without asking the directory.
		int asked = requests.get();
		assertThat(ifsc.lookup("HDFC1000001").status()).isEqualTo(IfscDirectory.Status.NOT_FOUND);
		assertThat(requests.get()).isEqualTo(asked);
		// Answers are cached.
		ifsc.lookup("HDFC0000001");
		assertThat(requests.get()).isEqualTo(asked);
	}

	@Test
	void anUnreachableDirectoryLeavesCodesUnchecked() {
		assertThat(lookups(true).lookup("DOWN0000001").status()).isEqualTo(IfscDirectory.Status.UNAVAILABLE);
		IfscDirectory gone = new IfscDirectory(new IfscProperties(true, URI.create("http://127.0.0.1:1"), Duration.ofSeconds(1)));
		assertThat(gone.lookup("HDFC0000001").status()).isEqualTo(IfscDirectory.Status.UNAVAILABLE);
		assertThat(lookups(false).lookup("HDFC0000001").status()).isEqualTo(IfscDirectory.Status.UNAVAILABLE);
		assertThat(requests.get()).isEqualTo(1);
	}

	@Test
	void bankNamesMatchHoweverTheyAreWritten() {
		assertThat(BankAccountService.sameBank("HDFC", "HDFC Bank")).isTrue();
		assertThat(BankAccountService.sameBank("hdfc bank ltd", "HDFC Bank")).isTrue();
		assertThat(BankAccountService.sameBank("SBI", "State Bank of India")).isTrue();
		assertThat(BankAccountService.sameBank("ICICI Bank", "ICICI Bank Limited")).isTrue();
		assertThat(BankAccountService.sameBank("Axis Bank", "HDFC Bank")).isFalse();
	}

}

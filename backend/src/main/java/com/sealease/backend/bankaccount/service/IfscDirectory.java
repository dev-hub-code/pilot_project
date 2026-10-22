package com.sealease.backend.bankaccount.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Looks IFSC codes up in the online directory of Indian bank branches (RBI's list, as published by
 * {@link IfscProperties#baseUrl()}). Only the IFSC is sent, never an account number or a name. Answers are cached:
 * branches rarely change, and the same codes come up again and again.
 */
@Service
public class IfscDirectory {

	private static final Logger log = LoggerFactory.getLogger(IfscDirectory.class);
	private static final Pattern IFSC = Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");
	private static final int CACHE_SIZE = 5_000;

	/** FOUND with its branch; NOT_FOUND when the directory has no such code; UNAVAILABLE when it could not be asked. */
	public enum Status { FOUND, NOT_FOUND, UNAVAILABLE }

	/** A branch: the bank's name, and the branch with its city and state for people to read. */
	public record Branch(String bank, String branch, String city, String state) {

		/** e.g. "TULSIANI CHMBRS - NARIMAN PT, GREATER MUMBAI, MAHARASHTRA". */
		public String place() {
			return String.join(", ", Stream.of(branch, city, state)
				.filter(part -> part != null && !part.isBlank()).toList());
		}
	}

	public record Lookup(Status status, Branch branch) {

		static final Lookup NOT_FOUND = new Lookup(Status.NOT_FOUND, null);
		static final Lookup UNAVAILABLE = new Lookup(Status.UNAVAILABLE, null);
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Answer(@JsonProperty("BANK") String bank, @JsonProperty("BRANCH") String branch,
			@JsonProperty("CITY") String city, @JsonProperty("STATE") String state) {
	}

	private final IfscProperties properties;
	private final RestClient client;
	private final Map<String, Lookup> cache = new ConcurrentHashMap<>();

	public IfscDirectory(IfscProperties properties) {
		this.properties = properties;
		JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
		requests.setReadTimeout(properties.timeout());
		this.client = RestClient.builder().baseUrl(properties.baseUrl().toString()).requestFactory(requests).build();
	}

	/** Whether codes are looked up at all (off in tests and wherever the directory cannot be reached). */
	public boolean enabled() {
		return properties.enabled();
	}

	/** Looks a code up; a code that is not even shaped like an IFSC is NOT_FOUND without asking. */
	public Lookup lookup(String code) {
		String ifsc = code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
		if (!IFSC.matcher(ifsc).matches()) {
			return Lookup.NOT_FOUND;
		}
		if (!properties.enabled()) {
			return Lookup.UNAVAILABLE;
		}
		Lookup cached = cache.get(ifsc);
		if (cached != null) {
			return cached;
		}
		Lookup result = ask(ifsc);
		if (result.status() != Status.UNAVAILABLE) {
			if (cache.size() >= CACHE_SIZE) {
				cache.clear();
			}
			cache.put(ifsc, result);
		}
		return result;
	}

	private Lookup ask(String ifsc) {
		try {
			Answer answer = client.get().uri("/{ifsc}", ifsc).retrieve().body(Answer.class);
			if (answer == null || answer.bank() == null) {
				return Lookup.NOT_FOUND;
			}
			return new Lookup(Status.FOUND, new Branch(answer.bank().strip(), trim(answer.branch()), trim(answer.city()),
					trim(answer.state())));
		} catch (HttpClientErrorException e) {
			if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
				return Lookup.NOT_FOUND;
			}
			log.warn("IFSC directory refused a lookup: {}", e.getStatusCode());
			return Lookup.UNAVAILABLE;
		} catch (RestClientException e) {
			log.warn("IFSC directory unavailable: {}", e.getMessage());
			return Lookup.UNAVAILABLE;
		}
	}

	private static String trim(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

}

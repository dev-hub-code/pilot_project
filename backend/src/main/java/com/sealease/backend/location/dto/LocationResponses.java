package com.sealease.backend.location.dto;

/** What the address dropdowns show, one level at a time: countries, a country's states, a state's cities. */
public final class LocationResponses {

	private LocationResponses() {
	}

	/** @param code ISO 3166-1 alpha-2, as addresses store it */
	public record Country(String code, String name, String phoneCode) {
	}

	/** @param id what the cities endpoint takes; addresses store the {@code name} */
	public record State(int id, String name, String code) {
	}

	public record City(int id, String name) {
	}

}

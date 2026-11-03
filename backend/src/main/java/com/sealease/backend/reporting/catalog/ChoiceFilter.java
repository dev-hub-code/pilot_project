package com.sealease.backend.reporting.catalog;

import java.util.List;

/**
 * A filter on one of a fixed set of values (a status, a method, a level): the values a request may name are only
 * these, bound as parameters.
 */
public record ChoiceFilter(String key, String label, String sql, List<String> values) {

	public ChoiceFilter {
		values = List.copyOf(values);
	}

}

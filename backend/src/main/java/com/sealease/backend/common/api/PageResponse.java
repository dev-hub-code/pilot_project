package com.sealease.backend.common.api;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Stable pagination envelope. Spring's {@link Page} is never serialised directly so that the API
 * contract does not change with Spring Data internals.
 */
public record PageResponse<T>(
		List<T> content,
		int page,
		int size,
		long totalElements,
		int totalPages,
		boolean first,
		boolean last) {

	public PageResponse {
		content = List.copyOf(content);
	}

	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
	}

	public static <E, T> PageResponse<T> from(Page<E> page, Function<? super E, ? extends T> mapper) {
		return from(page.map(mapper));
	}

}

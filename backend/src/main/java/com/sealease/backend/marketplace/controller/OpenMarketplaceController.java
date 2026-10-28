package com.sealease.backend.marketplace.controller;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.common.ratelimit.FixedWindowRateLimiter;
import com.sealease.backend.common.web.ClientInfo;
import com.sealease.backend.container.entity.ContainerType;
import com.sealease.backend.document.web.DocumentResponses;
import com.sealease.backend.investment.dto.RentTier;
import com.sealease.backend.investment.entity.ProductStatus;
import com.sealease.backend.investment.service.RentRateService;
import com.sealease.backend.marketplace.dto.MarketplaceCriteria;
import com.sealease.backend.marketplace.dto.MarketplaceSort;
import com.sealease.backend.marketplace.dto.OpenListing;
import com.sealease.backend.marketplace.service.MarketplaceService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The open API: the marketplace, read-only, for anyone without signing in (published at
 * https://www.sealease.in/api/open/v1 by the web app). Listings, their photos and the rent bands; nothing can be
 * bought, changed or seen about investors here.
 */
@RestController
@Validated
@RequestMapping("/api/v1/open")
public class OpenMarketplaceController {

	/** Requests per visitor per minute, across the open API. */
	static final int REQUESTS_PER_MINUTE = 120;

	private final MarketplaceService marketplace;
	private final RentRateService rentRates;
	private final FixedWindowRateLimiter perIp;

	public OpenMarketplaceController(MarketplaceService marketplace, RentRateService rentRates, Clock clock) {
		this.marketplace = marketplace;
		this.rentRates = rentRates;
		this.perIp = new FixedWindowRateLimiter(REQUESTS_PER_MINUTE, Duration.ofMinutes(1), clock);
	}

	/** Listed containers, open ones first by default; {@code status} CLOSED for those no longer sold. */
	@GetMapping("/marketplace")
	public PageResponse<OpenListing> listings(@RequestParam(required = false) ContainerType containerType,
			@RequestParam(required = false) ProductStatus status, @RequestParam(required = false) MarketplaceSort sort,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
		limit();
		return PageResponse.from(marketplace.search(new MarketplaceCriteria(containerType, status, sort),
				PageRequest.of(page, size)).map(OpenListing::from));
	}

	@GetMapping("/marketplace/{code}")
	public OpenListing listing(@PathVariable String code) {
		limit();
		return marketplace.openListing(code);
	}

	@GetMapping("/marketplace/{code}/photos/{photoId}")
	public ResponseEntity<byte[]> photo(@PathVariable String code, @PathVariable UUID photoId) {
		limit();
		return DocumentResponses.inline(marketplace.openPhoto(code, photoId));
	}

	/** Monthly rent by order total, lowest band first: an order earns its band's rate on every container. */
	@GetMapping("/rent-bands")
	public List<RentTier> rentBands() {
		limit();
		return rentRates.get().tiers();
	}

	private void limit() {
		String ip = ClientInfo.current().ipAddress();
		perIp.acquire(ip == null ? "unknown" : ip);
	}

}

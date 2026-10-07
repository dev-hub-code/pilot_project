package com.sealease.backend.marketplace.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.container.entity.ContainerType;
import com.sealease.backend.document.web.DocumentResponses;
import com.sealease.backend.investment.service.PlanRules;
import com.sealease.backend.investment.entity.ProductStatus;
import com.sealease.backend.marketplace.dto.MarketplaceCriteria;
import com.sealease.backend.marketplace.dto.MarketplaceDetail;
import com.sealease.backend.marketplace.dto.MarketplaceListing;
import com.sealease.backend.marketplace.dto.MarketplaceSort;
import com.sealease.backend.marketplace.dto.ReturnProjection;
import com.sealease.backend.marketplace.service.MarketplaceService;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Investors browse plans; staff with INVESTMENT_VIEW can preview exactly what investors see. */
@RestController
@RequestMapping("/api/v1/marketplace")
@PreAuthorize("hasAnyAuthority('INVESTOR_PORTAL', 'INVESTMENT_VIEW')")
public class MarketplaceController {

	private final MarketplaceService marketplace;

	public MarketplaceController(MarketplaceService marketplace) {
		this.marketplace = marketplace;
	}

	@GetMapping
	public PageResponse<MarketplaceListing> search(@RequestParam(required = false) ContainerType containerType,
			@RequestParam(required = false) ProductStatus status,
			@RequestParam(required = false) MarketplaceSort sort,
			@PageableDefault(size = 12) Pageable pageable) {
		return PageResponse.from(marketplace.search(
				new MarketplaceCriteria(containerType, status, sort), pageable));
	}

	@GetMapping("/{productId}")
	public MarketplaceDetail detail(AuthenticatedUser viewer, @PathVariable UUID productId) {
		return marketplace.detail(productId, viewer.userId());
	}

	@GetMapping("/{productId}/projection")
	public ReturnProjection projection(AuthenticatedUser viewer, @PathVariable UUID productId,
			@RequestParam @Min(0) @Max(PlanRules.MAX_CONTAINERS_PER_LINE) int containers) {
		return marketplace.project(productId, viewer.userId(), containers);
	}

	@GetMapping("/{productId}/documents/{documentId}")
	public ResponseEntity<byte[]> document(@PathVariable UUID productId, @PathVariable UUID documentId) {
		return DocumentResponses.inline(marketplace.photo(productId, documentId));
	}

}

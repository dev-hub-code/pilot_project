package com.sealease.backend.investment.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.common.api.ReasonRequest;
import com.sealease.backend.investment.dto.ActivateLeaseRequest;
import com.sealease.backend.investment.dto.CapacityMovementResponse;
import com.sealease.backend.investment.dto.ProductRequest;
import com.sealease.backend.investment.dto.ProductResponse;
import com.sealease.backend.investment.dto.ProductSearchCriteria;
import com.sealease.backend.investment.entity.InvestmentType;
import com.sealease.backend.investment.entity.ProductStatus;
import com.sealease.backend.investment.service.ProductService;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/investment-products")
public class AdminProductController {

	private final ProductService products;

	public AdminProductController(ProductService products) {
		this.products = products;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('INVESTMENT_VIEW')")
	public PageResponse<ProductResponse> search(@RequestParam(required = false) @Size(max = 50) String q,
			@RequestParam(required = false) ProductStatus status,
			@RequestParam(required = false) InvestmentType investmentType,
			@PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return PageResponse.from(products.search(new ProductSearchCriteria(q, status, investmentType), pageable));
	}

	@GetMapping("/{productId}")
	@PreAuthorize("hasAuthority('INVESTMENT_VIEW')")
	public ProductResponse detail(@PathVariable UUID productId) {
		return products.detail(productId);
	}

	@GetMapping("/{productId}/capacity-movements")
	@PreAuthorize("hasAuthority('INVESTMENT_VIEW')")
	public List<CapacityMovementResponse> movements(@PathVariable UUID productId) {
		return products.recentMovements(productId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('INVESTMENT_CREATE')")
	public ProductResponse create(AuthenticatedUser actor, @Valid @RequestBody ProductRequest request) {
		return products.create(actor.userId(), request);
	}

	@PutMapping("/{productId}")
	@PreAuthorize("hasAuthority('INVESTMENT_UPDATE')")
	public ProductResponse update(AuthenticatedUser actor, @PathVariable UUID productId,
			@Valid @RequestBody ProductRequest request) {
		return products.update(actor.userId(), productId, request);
	}

	/** Publishing makes terms binding and visible to investors, so it needs approval rights. */
	@PostMapping("/{productId}/publish")
	@PreAuthorize("hasAuthority('INVESTMENT_APPROVE')")
	public ProductResponse publish(AuthenticatedUser actor, @PathVariable UUID productId) {
		return products.publish(actor.userId(), productId);
	}

	@PostMapping("/{productId}/activate")
	@PreAuthorize("hasAuthority('INVESTMENT_APPROVE')")
	public ProductResponse activate(AuthenticatedUser actor, @PathVariable UUID productId,
			@Valid @RequestBody ActivateLeaseRequest request) {
		return products.activateLease(actor.userId(), productId, request.leaseStartsOn());
	}

	@PostMapping("/{productId}/cancel")
	@PreAuthorize("hasAuthority('INVESTMENT_APPROVE')")
	public ProductResponse cancel(AuthenticatedUser actor, @PathVariable UUID productId,
			@Valid @RequestBody ReasonRequest request) {
		return products.cancel(actor.userId(), productId, request.reason());
	}

}

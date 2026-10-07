package com.sealease.backend.earning.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.common.api.ReasonRequest;
import com.sealease.backend.earning.dto.DuePeriodResponse;
import com.sealease.backend.earning.dto.RecordRentalRequest;
import com.sealease.backend.earning.dto.RentalReceiptDetail;
import com.sealease.backend.earning.dto.RentalReceiptResponse;
import com.sealease.backend.earning.dto.RentalSearchCriteria;
import com.sealease.backend.earning.entity.ReceiptStatus;
import com.sealease.backend.earning.service.RentalService;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/rentals")
public class AdminRentalController {

	private static final String VIEW = "hasAnyAuthority('FINANCE_VIEW', 'RENTAL_RECORD', 'RENTAL_APPROVE')";

	private final RentalService rentals;

	public AdminRentalController(RentalService rentals) {
		this.rentals = rentals;
	}

	@GetMapping
	@PreAuthorize(VIEW)
	public PageResponse<RentalReceiptResponse> search(@RequestParam(required = false) ReceiptStatus status,
			@RequestParam(required = false) UUID productId,
			@PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return PageResponse.from(rentals.search(new RentalSearchCriteria(status, productId), pageable));
	}

	@GetMapping("/due")
	@PreAuthorize(VIEW)
	public List<DuePeriodResponse> due() {
		return rentals.due();
	}

	@GetMapping("/{receiptId}")
	@PreAuthorize(VIEW)
	public RentalReceiptDetail detail(@PathVariable UUID receiptId) {
		return rentals.detail(receiptId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('RENTAL_RECORD')")
	public RentalReceiptDetail record(AuthenticatedUser actor, @Valid @RequestBody RecordRentalRequest request) {
		return rentals.record(actor.userId(), request);
	}

	/** Releases the money to investors; must be someone other than the person who recorded it. */
	@PostMapping("/{receiptId}/approve")
	@PreAuthorize("hasAuthority('RENTAL_APPROVE')")
	public RentalReceiptDetail approve(AuthenticatedUser actor, @PathVariable UUID receiptId) {
		return rentals.approve(actor.userId(), receiptId);
	}

	@PostMapping("/{receiptId}/reject")
	@PreAuthorize("hasAnyAuthority('RENTAL_RECORD', 'RENTAL_APPROVE')")
	public RentalReceiptDetail reject(AuthenticatedUser actor, @PathVariable UUID receiptId,
			@Valid @RequestBody ReasonRequest request) {
		return rentals.reject(actor.userId(), actor.hasPermission("RENTAL_APPROVE"), receiptId, request.reason());
	}

}

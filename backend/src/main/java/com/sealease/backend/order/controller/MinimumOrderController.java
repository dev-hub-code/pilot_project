package com.sealease.backend.order.controller;

import com.sealease.backend.order.dto.MinimumOrderRequest;
import com.sealease.backend.order.dto.MinimumOrderResponse;
import com.sealease.backend.order.service.MinimumOrderService;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The minimum order value: shown to investors, changed by staff who manage plans. */
@RestController
public class MinimumOrderController {

	private final MinimumOrderService minimum;

	public MinimumOrderController(MinimumOrderService minimum) {
		this.minimum = minimum;
	}

	/** For the website and the marketplace; public. */
	@GetMapping("/api/v1/public/minimum-order")
	public MinimumOrderResponse forPublic() {
		return new MinimumOrderResponse(minimum.get().amount(), null, null);
	}

	@GetMapping("/api/v1/admin/settings/minimum-order")
	@PreAuthorize("hasAuthority('INVESTMENT_VIEW')")
	public MinimumOrderResponse get() {
		return minimum.get();
	}

	/** Applies to orders placed from now on. */
	@PutMapping("/api/v1/admin/settings/minimum-order")
	@PreAuthorize("hasAuthority('INVESTMENT_UPDATE')")
	public MinimumOrderResponse update(AuthenticatedUser admin, @Valid @RequestBody MinimumOrderRequest request) {
		return minimum.update(admin.userId(), request.amount());
	}

}

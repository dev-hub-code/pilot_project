package com.sealease.backend.payment.controller;

import com.sealease.backend.security.AuthenticatedUser;
import com.sealease.backend.payment.dto.PaymentGatewaySettingsRequest;
import com.sealease.backend.payment.dto.PaymentGatewaySettingsResponse;
import com.sealease.backend.payment.service.PaymentGatewaySettingsService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * How online payments through Razorpay run: finance may look, those who manage system settings change them. The API
 * keys and webhook secret are not settable here; they stay in the server's environment.
 */
@RestController
public class AdminPaymentGatewaySettingsController {

	static final String PATH = "/api/v1/admin/settings/payment-gateway";

	private final PaymentGatewaySettingsService settings;

	public AdminPaymentGatewaySettingsController(PaymentGatewaySettingsService settings) {
		this.settings = settings;
	}

	@GetMapping(PATH)
	@PreAuthorize("hasAnyAuthority('SYSTEM_SETTINGS_MANAGE', 'FINANCE_VIEW')")
	public PaymentGatewaySettingsResponse get() {
		return settings.view();
	}

	/** Applies to payments started from now on. */
	@PutMapping(PATH)
	@PreAuthorize("hasAuthority('SYSTEM_SETTINGS_MANAGE')")
	public PaymentGatewaySettingsResponse update(AuthenticatedUser admin,
			@Valid @RequestBody PaymentGatewaySettingsRequest request) {
		return settings.update(admin.userId(), request);
	}

}

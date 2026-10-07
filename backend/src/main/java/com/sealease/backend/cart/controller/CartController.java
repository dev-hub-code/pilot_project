package com.sealease.backend.cart.controller;

import com.sealease.backend.cart.dto.CartResponse;
import com.sealease.backend.cart.dto.SetCartItemRequest;
import com.sealease.backend.cart.service.CartService;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cart")
@PreAuthorize("hasAuthority('INVESTOR_PORTAL')")
public class CartController {

	private final CartService cart;

	public CartController(CartService cart) {
		this.cart = cart;
	}

	@GetMapping
	public CartResponse view(AuthenticatedUser investor) {
		return cart.view(investor.userId());
	}

	@PutMapping("/items/{productId}")
	public CartResponse setItem(AuthenticatedUser investor, @PathVariable UUID productId,
			@Valid @RequestBody SetCartItemRequest request) {
		return cart.setItem(investor.userId(), productId, request.amount());
	}

	@DeleteMapping("/items/{productId}")
	public CartResponse removeItem(AuthenticatedUser investor, @PathVariable UUID productId) {
		return cart.removeItem(investor.userId(), productId);
	}

}

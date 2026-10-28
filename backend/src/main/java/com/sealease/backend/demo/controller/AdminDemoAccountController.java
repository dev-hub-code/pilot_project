package com.sealease.backend.demo.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sealease.backend.demo.dto.DemoAccountRequest;
import com.sealease.backend.demo.dto.DemoAccountSummary;
import com.sealease.backend.demo.dto.DemoAccountView;
import com.sealease.backend.demo.dto.DemoFrameRequest;
import com.sealease.backend.demo.dto.DemoMemberRequest;
import com.sealease.backend.demo.dto.DemoOrderRequest;
import com.sealease.backend.demo.service.DemoAccountService;
import com.sealease.backend.security.AuthenticatedUser;

import jakarta.validation.Valid;

/** Demo accounts and their dummy orders and downline: super administrators only. */
@RestController
@RequestMapping("/api/v1/admin/demo-accounts")
@PreAuthorize("hasAuthority('DEMO_ACCOUNT_MANAGE')")
public class AdminDemoAccountController {

	private final DemoAccountService demos;

	public AdminDemoAccountController(DemoAccountService demos) {
		this.demos = demos;
	}

	@GetMapping
	public List<DemoAccountSummary> list() {
		return demos.list();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public DemoAccountView create(AuthenticatedUser actor, @Valid @RequestBody DemoAccountRequest request) {
		return demos.create(actor.userId(), request);
	}

	@GetMapping("/{userId}")
	public DemoAccountView view(@PathVariable UUID userId) {
		return demos.view(userId);
	}

	@PutMapping("/{userId}")
	public DemoAccountView changeFrame(AuthenticatedUser actor, @PathVariable UUID userId,
			@Valid @RequestBody DemoFrameRequest request) {
		return demos.changeFrame(actor.userId(), userId, request);
	}

	/** No longer a demo account; its dummy orders and downline are deleted. */
	@DeleteMapping("/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void remove(AuthenticatedUser actor, @PathVariable UUID userId) {
		demos.remove(actor.userId(), userId);
	}

	@PostMapping("/{userId}/orders")
	public DemoAccountView addOrder(AuthenticatedUser actor, @PathVariable UUID userId,
			@Valid @RequestBody DemoOrderRequest request) {
		return demos.addOrder(actor.userId(), userId, request);
	}

	@DeleteMapping("/{userId}/orders/{orderId}")
	public DemoAccountView removeOrder(AuthenticatedUser actor, @PathVariable UUID userId, @PathVariable UUID orderId) {
		return demos.removeOrder(actor.userId(), userId, orderId);
	}

	@PostMapping("/{userId}/members")
	public DemoAccountView addMember(AuthenticatedUser actor, @PathVariable UUID userId,
			@Valid @RequestBody DemoMemberRequest request) {
		return demos.addMember(actor.userId(), userId, request);
	}

	/** Removes the member with everyone below them and their orders. */
	@DeleteMapping("/{userId}/members/{memberId}")
	public DemoAccountView removeMember(AuthenticatedUser actor, @PathVariable UUID userId, @PathVariable UUID memberId) {
		return demos.removeMember(actor.userId(), userId, memberId);
	}

}

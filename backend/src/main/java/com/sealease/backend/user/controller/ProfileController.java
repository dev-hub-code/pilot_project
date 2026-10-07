package com.sealease.backend.user.controller;

import com.sealease.backend.security.AuthenticatedUser;
import com.sealease.backend.user.dto.ProfileResponse;
import com.sealease.backend.user.dto.UpdateProfileRequest;
import com.sealease.backend.user.dto.UpdateTaxInfoRequest;
import com.sealease.backend.user.service.UserProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own profile. Any authenticated account (investor or staff) may use it. */
@RestController
@RequestMapping("/api/v1/users/me")
public class ProfileController {

	private final UserProfileService profiles;

	public ProfileController(UserProfileService profiles) {
		this.profiles = profiles;
	}

	@GetMapping
	public ProfileResponse get(AuthenticatedUser user) {
		return profiles.getProfile(user.userId());
	}

	@PutMapping
	public ProfileResponse update(AuthenticatedUser user, @Valid @RequestBody UpdateProfileRequest request) {
		return profiles.update(user.userId(), request);
	}

	@PutMapping("/tax")
	public ProfileResponse updateTax(AuthenticatedUser user, @Valid @RequestBody UpdateTaxInfoRequest request) {
		return profiles.updateTax(user.userId(), request);
	}

}

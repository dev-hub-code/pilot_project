package com.sealease.backend.user.service;

import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Password rules following NIST SP 800-63B: length over composition rules. Long passphrases are
 * allowed; obviously weak choices (containing the email name, a single repeated character) are not.
 * Bean Validation on the request DTOs enforces the same length bounds earlier.
 */
@Component
public class PasswordPolicy {

	public static final int MIN_LENGTH = 12;
	public static final int MAX_LENGTH = 128;

	public void validate(String password, String email) {
		if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
			throw weak("Password must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters");
		}
		if (password.isBlank() || password.chars().distinct().count() < 4) {
			throw weak("Password is too simple");
		}
		String localPart = email == null ? "" : email.toLowerCase(Locale.ROOT).split("@", 2)[0];
		if (localPart.length() >= 4 && password.toLowerCase(Locale.ROOT).contains(localPart)) {
			throw weak("Password must not contain your email address");
		}
	}

	private static BusinessException weak(String message) {
		return new BusinessException(ErrorCode.VALIDATION_FAILED, message);
	}

}

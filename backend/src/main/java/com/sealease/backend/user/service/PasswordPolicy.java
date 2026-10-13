package com.sealease.backend.user.service;

import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Password rules for new passwords: 6 to 18 characters, alphanumeric (letters and at least one number)
 * with at least one uppercase letter and one special character (anything but a letter, digit or space), and not obviously weak (containing the
 * email name, too few different characters). Bean Validation on the request DTOs enforces the length
 * and composition earlier, so the form shows them per field. Sign-in does not apply these rules, so
 * passwords set under earlier rules keep working.
 */
@Component
public class PasswordPolicy {

	public static final int MIN_LENGTH = 6;
	public static final int MAX_LENGTH = 18;

	/** Length plus at least one uppercase letter, one number and one special character; for {@code @Pattern} on request DTOs. */
	public static final String PATTERN = "(?=.*\\p{Lu})(?=.*\\p{Nd})(?=.*[^\\p{L}\\p{N}\\s]).{" + MIN_LENGTH + "," + MAX_LENGTH + "}";

	public static final String RULES_MESSAGE = "Use " + MIN_LENGTH + " to " + MAX_LENGTH
			+ " characters with at least one uppercase letter, one number and one special character";

	private static final Pattern COMPOSITION_PATTERN = Pattern.compile(PATTERN, Pattern.DOTALL);

	public void validate(String password, String email) {
		if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH
				|| !COMPOSITION_PATTERN.matcher(password).matches()) {
			throw weak("Password: " + RULES_MESSAGE.substring(0, 1).toLowerCase(Locale.ROOT) + RULES_MESSAGE.substring(1));
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

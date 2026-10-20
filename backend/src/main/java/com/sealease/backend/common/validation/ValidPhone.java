package com.sealease.backend.common.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * The request's {@code phone} is a valid number of its {@code phoneCountry} (see {@link PhoneNumbers#parse}) and, with
 * {@code mobile = true}, a mobile number. Reported on the {@code phone} field. No number is valid; combine with
 * {@code @NotBlank} on the field to require one.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidPhone.Validator.class)
public @interface ValidPhone {

	String message() default "Enter a valid mobile number for the selected country";

	/** Only mobile numbers (accounts); otherwise any valid number (leads, which may give a landline). */
	boolean mobile() default true;

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	/** A request carrying a phone number and the country it was typed in. */
	interface Input {

		String phone();

		String phoneCountry();

	}

	class Validator implements ConstraintValidator<ValidPhone, Input> {

		private boolean mobile;

		@Override
		public void initialize(ValidPhone annotation) {
			mobile = annotation.mobile();
		}

		@Override
		public boolean isValid(Input value, ConstraintValidatorContext context) {
			if (value == null || value.phone() == null || value.phone().isBlank()) {
				return true;
			}
			boolean valid = PhoneNumbers.parse(value.phone(), value.phoneCountry())
				.map(phone -> !mobile || phone.mobile())
				.orElse(false);
			if (!valid) {
				context.disableDefaultConstraintViolation();
				context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
					.addPropertyNode("phone")
					.addConstraintViolation();
			}
			return valid;
		}

	}

}

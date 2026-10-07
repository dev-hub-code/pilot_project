package com.sealease.backend.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Locale;
import java.util.Set;

/** ISO 3166-1 alpha-2 country code (upper case). {@code null} is valid; combine with {@code @NotNull}. */
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = IsoCountry.Validator.class)
public @interface IsoCountry {

	String message() default "must be an ISO 3166-1 alpha-2 country code";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validator implements ConstraintValidator<IsoCountry, String> {

		private static final Set<String> CODES = Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA2);

		@Override
		public boolean isValid(String value, ConstraintValidatorContext context) {
			return value == null || CODES.contains(value);
		}

	}

}

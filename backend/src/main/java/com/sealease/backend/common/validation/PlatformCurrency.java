package com.sealease.backend.common.validation;

import com.sealease.backend.common.money.MoneyProperties;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The platform's single currency ({@code app.money.default-currency}, INR). Every amount on the
 * platform is in it. {@code null} is valid; combine with {@code @NotBlank}.
 */
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PlatformCurrency.Validator.class)
public @interface PlatformCurrency {

	String message() default "must be the platform currency";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validator implements ConstraintValidator<PlatformCurrency, String> {

		private final MoneyProperties money;

		public Validator(MoneyProperties money) {
			this.money = money;
		}

		@Override
		public boolean isValid(String value, ConstraintValidatorContext context) {
			if (value == null || money.defaultCurrency().getCurrencyCode().equals(value)) {
				return true;
			}
			context.disableDefaultConstraintViolation();
			context.buildConstraintViolationWithTemplate("must be " + money.defaultCurrency().getCurrencyCode())
				.addConstraintViolation();
			return false;
		}

	}

}

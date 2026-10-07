package com.sealease.backend.payment.provider;

import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;

public class InvalidWebhookException extends BusinessException {

	public InvalidWebhookException(String message) {
		super(ErrorCode.UNAUTHORIZED, message);
	}

}

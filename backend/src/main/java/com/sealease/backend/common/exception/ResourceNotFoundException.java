package com.sealease.backend.common.exception;

public class ResourceNotFoundException extends BusinessException {

	public ResourceNotFoundException(String resource, Object id) {
		super(ErrorCode.NOT_FOUND, resource + " not found: " + id);
	}

}

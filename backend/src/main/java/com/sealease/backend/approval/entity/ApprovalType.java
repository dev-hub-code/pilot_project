package com.sealease.backend.approval.entity;

import com.sealease.backend.permission.PermissionCode;

/** Operations that can wait for a second person, and the permission both people must hold. */
public enum ApprovalType {

	LEDGER_ADJUSTMENT(PermissionCode.FINANCE_ADJUST),
	PAYMENT_REFUND(PermissionCode.FINANCE_ADJUST);

	private final PermissionCode permission;

	ApprovalType(PermissionCode permission) {
		this.permission = permission;
	}

	public PermissionCode permission() {
		return permission;
	}

}

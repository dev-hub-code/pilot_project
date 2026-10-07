package com.sealease.backend.payment.entity;

import com.sealease.backend.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * An account the company collects investors' money in. Shown to every investor paying by bank, so
 * the details are stored in clear. Deactivated, never deleted: past payments refer to it.
 */
@Entity
@Table(name = "company_bank_accounts")
public class CompanyBankAccount extends BaseEntity {

	@Column(name = "account_name", nullable = false, length = 140)
	private String accountName;

	@Column(name = "bank_name", nullable = false, length = 140)
	private String bankName;

	@Column(name = "branch", length = 140)
	private String branch;

	@Column(name = "account_number", nullable = false, length = 34)
	private String accountNumber;

	@Column(name = "ifsc_code", nullable = false, length = 11)
	private String ifscCode;

	@Column(name = "upi_id", length = 100)
	private String upiId;

	@Column(name = "active", nullable = false)
	private boolean active;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	protected CompanyBankAccount() {
	}

	public CompanyBankAccount(UUID createdBy, Details details) {
		this.createdBy = createdBy;
		this.active = true;
		update(details);
	}

	public void update(Details details) {
		this.accountName = details.accountName();
		this.bankName = details.bankName();
		this.branch = details.branch();
		this.accountNumber = details.accountNumber();
		this.ifscCode = details.ifscCode();
		this.upiId = details.upiId();
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public Details details() {
		return new Details(accountName, bankName, branch, accountNumber, ifscCode, upiId);
	}

	public String getAccountName() {
		return accountName;
	}

	public String getBankName() {
		return bankName;
	}

	public String getBranch() {
		return branch;
	}

	public String getAccountNumber() {
		return accountNumber;
	}

	public String getIfscCode() {
		return ifscCode;
	}

	public String getUpiId() {
		return upiId;
	}

	public boolean isActive() {
		return active;
	}

	/** The editable details, already normalised. */
	public record Details(String accountName, String bankName, String branch, String accountNumber, String ifscCode,
			String upiId) {
	}

}

package com.sealease.backend.user.entity;

import com.sealease.backend.common.persistence.BaseEntity;
import com.sealease.backend.common.validation.PhoneNumbers;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** Personal, contact and tax data of an account (1:1 with {@link User}). */
@Entity
@Table(name = "user_profiles")
public class UserProfile extends BaseEntity {

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, updatable = false)
	private User user;

	/** The mobile number in international (E.164) form, e.g. "+919876543210". */
	@Column(name = "phone", length = 20)
	private String phone;

	/** The phone number in comparable form (unique across accounts); see {@code PhoneNumbers.Phone#key}. */
	@Column(name = "phone_key", length = 20)
	private String phoneKey;

	/** The country of the mobile number (ISO 3166-1 alpha-2, a row of {@code countries}). */
	@Column(name = "phone_country", length = 2)
	private String phoneCountry;

	@Column(name = "date_of_birth")
	private LocalDate dateOfBirth;

	@Column(name = "nationality", length = 2)
	private String nationality;

	@Column(name = "address_line1", length = 200)
	private String addressLine1;

	@Column(name = "address_line2", length = 200)
	private String addressLine2;

	@Column(name = "city", length = 100)
	private String city;

	@Column(name = "state_region", length = 100)
	private String stateRegion;

	@Column(name = "postal_code", length = 20)
	private String postalCode;

	@Column(name = "country", length = 2)
	private String country;

	@Column(name = "tax_residency_country", length = 2)
	private String taxResidencyCountry;

	@Column(name = "tax_id_encrypted", columnDefinition = "TEXT")
	private String taxIdEncrypted;

	@Column(name = "tax_id_last4", length = 4)
	private String taxIdLast4;

	@Enumerated(EnumType.STRING)
	@Column(name = "kyc_status", nullable = false, length = 20)
	private KycStatus kycStatus = KycStatus.NOT_SUBMITTED;

	@Column(name = "email_notifications", nullable = false)
	private boolean emailNotifications = true;

	@Column(name = "sms_notifications", nullable = false)
	private boolean smsNotifications;

	protected UserProfile() {
	}

	public UserProfile(User user) {
		this.user = user;
	}

	/** @param phone the mobile number, or null for none */
	public void updatePersonal(PhoneNumbers.Phone phone, LocalDate dateOfBirth, String nationality) {
		this.phone = phone == null ? null : phone.e164();
		this.phoneKey = phone == null ? null : phone.key();
		this.phoneCountry = phone == null ? null : phone.country();
		this.dateOfBirth = dateOfBirth;
		this.nationality = nationality;
	}

	public void updateAddress(String line1, String line2, String city, String stateRegion, String postalCode,
			String country) {
		this.addressLine1 = line1;
		this.addressLine2 = line2;
		this.city = city;
		this.stateRegion = stateRegion;
		this.postalCode = postalCode;
		this.country = country;
	}

	public void updateNotificationPreferences(boolean email, boolean sms) {
		this.emailNotifications = email;
		this.smsNotifications = sms;
	}

	public void updateTax(String residencyCountry, String taxIdEncrypted, String taxIdLast4) {
		this.taxResidencyCountry = residencyCountry;
		this.taxIdEncrypted = taxIdEncrypted;
		this.taxIdLast4 = taxIdLast4;
	}

	public void markKyc(KycStatus status) {
		this.kycStatus = status;
	}

	public User getUser() {
		return user;
	}

	public String getPhone() {
		return phone;
	}

	public String getPhoneCountry() {
		return phoneCountry;
	}

	public LocalDate getDateOfBirth() {
		return dateOfBirth;
	}

	public String getNationality() {
		return nationality;
	}

	public String getAddressLine1() {
		return addressLine1;
	}

	public String getAddressLine2() {
		return addressLine2;
	}

	public String getCity() {
		return city;
	}

	public String getStateRegion() {
		return stateRegion;
	}

	public String getPostalCode() {
		return postalCode;
	}

	public String getCountry() {
		return country;
	}

	public String getTaxResidencyCountry() {
		return taxResidencyCountry;
	}

	public String getTaxIdLast4() {
		return taxIdLast4;
	}

	public KycStatus getKycStatus() {
		return kycStatus;
	}

	public boolean isEmailNotifications() {
		return emailNotifications;
	}

	public boolean isSmsNotifications() {
		return smsNotifications;
	}

}

package com.sealease.backend.agreement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * One version of the lease agreement's wording, with {{placeholders}} for each investment's facts. Never edited: a
 * change is a new version, and the latest version is used for agreements issued from then on.
 */
@Entity
@Immutable
@Table(name = "agreement_templates")
public class AgreementTemplate {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.TIME)
	private UUID id;

	@Column(name = "version", nullable = false)
	private int version;

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Column(name = "body", nullable = false, columnDefinition = "TEXT")
	private String body;

	@Column(name = "created_by")
	private UUID createdBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected AgreementTemplate() {
	}

	public AgreementTemplate(int version, String title, String body, UUID createdBy, Instant createdAt) {
		this.version = version;
		this.title = title;
		this.body = body;
		this.createdBy = createdBy;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public int getVersion() {
		return version;
	}

	public String getTitle() {
		return title;
	}

	public String getBody() {
		return body;
	}

	/** {@code null} for the wording the platform shipped with. */
	public UUID getCreatedBy() {
		return createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}

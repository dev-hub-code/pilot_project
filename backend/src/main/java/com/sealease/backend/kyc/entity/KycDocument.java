package com.sealease.backend.kyc.entity;

import com.sealease.backend.document.entity.DocumentPurpose;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "kyc_documents")
public class KycDocument {

	@EmbeddedId
	private KycDocumentId id;

	@Enumerated(EnumType.STRING)
	@Column(name = "purpose", nullable = false, length = 40)
	private DocumentPurpose purpose;

	protected KycDocument() {
	}

	public KycDocument(KycDocumentId id, DocumentPurpose purpose) {
		this.id = id;
		this.purpose = purpose;
	}

	public KycDocumentId getId() {
		return id;
	}

	public DocumentPurpose getPurpose() {
		return purpose;
	}

}

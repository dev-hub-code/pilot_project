package com.sealease.backend.document.repository;

import com.sealease.backend.document.entity.StoredDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StoredDocumentRepository extends JpaRepository<StoredDocument, UUID> {
}

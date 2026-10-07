package com.sealease.backend.invoice.repository;

import com.sealease.backend.invoice.entity.InvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, UUID> {

	List<InvoiceLine> findByInvoiceIdOrderByLineNumber(UUID invoiceId);

}

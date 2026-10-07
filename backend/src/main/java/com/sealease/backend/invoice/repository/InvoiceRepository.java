package com.sealease.backend.invoice.repository;

import com.sealease.backend.invoice.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

	Optional<Invoice> findByOrderId(UUID orderId);

	@Query(value = "select nextval('invoice_number_seq')", nativeQuery = true)
	long nextNumber();

}

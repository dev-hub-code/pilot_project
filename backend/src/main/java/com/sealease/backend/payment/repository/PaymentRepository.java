package com.sealease.backend.payment.repository;

import com.sealease.backend.payment.entity.Payment;
import com.sealease.backend.payment.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Payment p where p.id = :id")
	Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

	Optional<Payment> findByProviderAndProviderReference(String provider, String providerReference);

	Optional<Payment> findByOrderIdAndIdempotencyKey(UUID orderId, String idempotencyKey);

	List<Payment> findByOrderIdOrderByCreatedAtDesc(UUID orderId);

	List<Payment> findByOrderIdAndStatus(UUID orderId, PaymentStatus status);

}

package com.sealease.backend.payment.repository;

import com.sealease.backend.payment.entity.Payment;
import com.sealease.backend.payment.entity.PaymentMethod;
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

	/**
	 * A payment's fixed facts, read without loading the payment: a flow that then locks it ({@link #findByIdForUpdate})
	 * gets the row as it is under the lock, never a copy read earlier in the same transaction.
	 */
	interface PaymentKeys {

		UUID getId();

		UUID getOrderId();

		UUID getUserId();

		PaymentMethod getMethod();

	}

	@Query("select p.id as id, p.orderId as orderId, p.userId as userId, p.method as method from Payment p where p.id = :id")
	Optional<PaymentKeys> findKeysById(@Param("id") UUID id);

	@Query("""
			select p.id as id, p.orderId as orderId, p.userId as userId, p.method as method from Payment p
			where p.provider = :provider and p.providerReference = :reference""")
	Optional<PaymentKeys> findKeysByProviderReference(@Param("provider") String provider,
			@Param("reference") String providerReference);

	Optional<Payment> findByOrderIdAndIdempotencyKey(UUID orderId, String idempotencyKey);

	/** The payment recording a payment at the provider (its id, kept as the external reference), if recorded. */
	Optional<Payment> findByProviderAndExternalReference(String provider, String externalReference);

	/**
	 * Payments of this method other than {@code excluded} already recorded with this bank reference or receipt number
	 * (any case): their own references, for the message.
	 */
	@Query("""
			select p.providerReference from Payment p
			where p.method = :method and upper(p.externalReference) = upper(:reference) and p.id <> :excluded""")
	List<String> findOthersWithExternalReference(@Param("method") PaymentMethod method,
			@Param("reference") String externalReference, @Param("excluded") UUID excluded);

	List<Payment> findByOrderIdOrderByCreatedAtDesc(UUID orderId);

	List<Payment> findByOrderIdAndStatus(UUID orderId, PaymentStatus status);

}

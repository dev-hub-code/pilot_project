package com.sealease.backend.earning.repository;

import com.sealease.backend.earning.entity.ReceiptStatus;
import com.sealease.backend.earning.entity.RentalReceipt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RentalReceiptRepository
		extends JpaRepository<RentalReceipt, UUID>, JpaSpecificationExecutor<RentalReceipt> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from RentalReceipt r where r.id = :id")
	Optional<RentalReceipt> findByIdForUpdate(@Param("id") UUID id);

	boolean existsByProductIdAndPeriodNumberAndStatusNot(UUID productId, int periodNumber, ReceiptStatus status);

	long countByProductIdAndStatus(UUID productId, ReceiptStatus status);

	List<RentalReceipt> findByProductIdInAndStatusNot(Collection<UUID> productIds, ReceiptStatus status);

}

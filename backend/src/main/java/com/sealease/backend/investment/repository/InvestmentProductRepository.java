package com.sealease.backend.investment.repository;

import com.sealease.backend.investment.entity.InvestmentProduct;
import com.sealease.backend.investment.entity.ProductStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvestmentProductRepository
		extends JpaRepository<InvestmentProduct, UUID>, JpaSpecificationExecutor<InvestmentProduct> {

	/** Row lock serialising status changes of one plan. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from InvestmentProduct p where p.id = :id")
	Optional<InvestmentProduct> findByIdForUpdate(@Param("id") UUID id);

	List<InvestmentProduct> findByStatusOrderByCode(ProductStatus status);

	@Query(value = "select nextval('investment_product_code_seq')", nativeQuery = true)
	long nextCodeNumber();

}

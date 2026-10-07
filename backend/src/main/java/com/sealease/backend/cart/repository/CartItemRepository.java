package com.sealease.backend.cart.repository;

import com.sealease.backend.cart.entity.CartItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

	List<CartItem> findByUserIdOrderByCreatedAt(UUID userId);

	Optional<CartItem> findByUserIdAndProductId(UUID userId, UUID productId);

	/** Locks the cart for checkout, so two concurrent checkouts of one cart cannot both succeed. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from CartItem c where c.userId = :userId order by c.createdAt")
	List<CartItem> findByUserIdForUpdate(@Param("userId") UUID userId);

	@Modifying
	@Query("delete from CartItem c where c.userId = :userId")
	int deleteAllOfUser(@Param("userId") UUID userId);

}

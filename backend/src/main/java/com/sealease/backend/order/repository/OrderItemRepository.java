package com.sealease.backend.order.repository;

import com.sealease.backend.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

	/** Ordered by offering id: the lock order used for capacity changes, which prevents deadlocks. */
	List<OrderItem> findByOrderIdOrderByProductIdAscIdAsc(UUID orderId);

	List<OrderItem> findByOrderIdIn(Collection<UUID> orderIds);

}

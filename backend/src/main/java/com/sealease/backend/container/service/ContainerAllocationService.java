package com.sealease.backend.container.service;

import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.container.dto.ContainerSummary;
import com.sealease.backend.container.entity.Container;
import com.sealease.backend.container.entity.ContainerStatus;
import com.sealease.backend.container.entity.ContainerType;
import com.sealease.backend.container.repository.ContainerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Inventory for the order and investment modules: containers are reserved for an order at
 * checkout, leased to the investor once it is paid, and returned to inventory when the order lapses
 * or the lease ends. All calls run in the caller's transaction.
 */
@Service
public class ContainerAllocationService {

	private final ContainerRepository containers;

	public ContainerAllocationService(ContainerRepository containers) {
		this.containers = containers;
	}

	/**
	 * Reserves {@code count} available containers of the type for the order, or none at all.
	 *
	 * @return the reserved containers, oldest first
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public List<ContainerSummary> reserve(ContainerType type, int count, UUID orderId) {
		List<Container> found = containers.lockAvailable(type.name(), count);
		if (found.size() < count) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, found.isEmpty()
					? "No " + type.label() + " containers are available right now"
					: "Only " + found.size() + " " + type.label() + " container(s) are available right now");
		}
		found.forEach(c -> c.reserve(orderId));
		containers.flush();
		return found.stream().map(ContainerSummary::from).toList();
	}

	/** Returns the order's reserved containers to inventory (the order lapsed or was cancelled). */
	@Transactional(propagation = Propagation.MANDATORY)
	public void release(UUID orderId) {
		containers.findByReservedOrderId(orderId).forEach(Container::releaseReservation);
		containers.flush();
	}

	/** The order was paid: its reserved container now belongs to the investor's lease. */
	@Transactional(propagation = Propagation.MANDATORY)
	public ContainerSummary lease(UUID containerId, UUID orderId) {
		Container container = lock(containerId);
		if (!orderId.equals(container.getReservedOrderId())) {
			throw new IllegalStateException("Container " + container.getContainerNumber() + " is not reserved for order " + orderId);
		}
		container.lease();
		containers.flush();
		return ContainerSummary.from(container);
	}

	/** The lease has run its tenure: the container goes back to inventory. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void endLease(UUID containerId) {
		Container container = lock(containerId);
		if (container.getStatus() == ContainerStatus.ON_LEASE) {
			container.endLease();
			containers.flush();
		}
	}

	@Transactional(readOnly = true)
	public long available(ContainerType type) {
		return containers.countByContainerTypeAndStatus(type, ContainerStatus.AVAILABLE);
	}

	@Transactional(readOnly = true)
	public Map<ContainerType, Long> availableByType() {
		Map<ContainerType, Long> counts = new EnumMap<>(ContainerType.class);
		for (Object[] row : containers.countByTypeWithStatus(ContainerStatus.AVAILABLE)) {
			counts.put((ContainerType) row[0], (Long) row[1]);
		}
		return counts;
	}

	private Container lock(UUID containerId) {
		return containers.findByIdForUpdate(containerId)
			.orElseThrow(() -> new ResourceNotFoundException("Container", containerId));
	}

}

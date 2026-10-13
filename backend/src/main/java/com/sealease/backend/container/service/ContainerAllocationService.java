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

import java.util.List;
import java.util.UUID;

/**
 * Containers for the order and investment modules. Finance types the number of the container it assigns to each paid
 * investment, which records and leases it; it becomes available again when the lease ends. (Orders placed before
 * manual assignment reserved containers at checkout; those are still leased on payment or released when the order
 * lapses.) All changes run in the caller's transaction.
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

	/**
	 * Leases the container finance typed in to a paid investment. A number seen before (a container whose earlier lease
	 * has ended) is reused if it is free and of the right type; a new number is recorded.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public ContainerSummary assign(String containerNumber, ContainerType type, String currentLocation, UUID actorId) {
		// Any format is accepted; spacing, dashes and case are tidied so one container is recorded once.
		String number = ContainerNumbers.normalize(containerNumber);
		if (number == null || number.isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Enter the container number");
		}
		String location = currentLocation == null || currentLocation.isBlank() ? null : currentLocation.strip();
		Container container = containers.findByContainerNumberForUpdate(number).orElse(null);
		if (container == null) {
			container = new Container(number, type, actorId);
			container.locatedAt(location);
			containers.saveAndFlush(container);
		}
		else {
			if (container.getContainerType() != type) {
				throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Container " + number + " is a "
						+ container.getContainerType().label() + "; this investment needs a " + type.label());
			}
			if (container.getStatus() != ContainerStatus.AVAILABLE) {
				throw new BusinessException(ErrorCode.CONFLICT, "Container " + number + " is already leased to an investor");
			}
			if (location != null) {
				container.locatedAt(location);
			}
		}
		container.assign();
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

	private Container lock(UUID containerId) {
		return containers.findByIdForUpdate(containerId)
			.orElseThrow(() -> new ResourceNotFoundException("Container", containerId));
	}

}

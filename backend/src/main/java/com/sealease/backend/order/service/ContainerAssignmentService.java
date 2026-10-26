package com.sealease.backend.order.service;

import com.sealease.backend.agreement.dto.LeaseAgreementResponse;
import com.sealease.backend.common.api.Assignee;
import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.container.service.ContainerAllocationService;
import com.sealease.backend.earning.service.RentCreditSettingsService;
import com.sealease.backend.investment.dto.HoldingResponse;
import com.sealease.backend.investment.service.HoldingService;
import com.sealease.backend.investment.service.HoldingService.AwaitingContainer;
import com.sealease.backend.notification.service.NotificationService;
import com.sealease.backend.order.dto.ContainerAssignmentQueue;
import com.sealease.backend.order.dto.OrderResponse;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.service.UserAccountService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Finance assigns a container to each paid investment (CONTAINER_ASSIGN) by typing its number (or generating a new
 * one), once a finance member
 * has taken the task (only they may then assign it). Assigning leases the
 * container to the investor and issues the lease agreement (in the wording admins maintain); the investor is notified
 * and starts the lease, and the payouts, by accepting it.
 */
@Service
public class ContainerAssignmentService {

	private final HoldingService holdings;
	private final OrderService orders;
	private final UserAccountService accounts;
	private final NotificationService notifications;
	private final LeaseAgreementIssuer agreements;
	private final RentCreditSettingsService credits;
	private final ContainerAllocationService containers;
	private final Clock clock;

	public ContainerAssignmentService(HoldingService holdings,
			OrderService orders, UserAccountService accounts, NotificationService notifications,
			LeaseAgreementIssuer agreements, RentCreditSettingsService credits, ContainerAllocationService containers,
			Clock clock) {
		this.holdings = holdings;
		this.containers = containers;
		this.credits = credits;
		this.agreements = agreements;
		this.orders = orders;
		this.accounts = accounts;
		this.notifications = notifications;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public ContainerAssignmentQueue queue(Pageable pageable) {
		Page<AwaitingContainer> page = holdings.awaitingContainer(pageable);
		Map<UUID, String> orderNumbers = orders.orderNumbers(page.stream().map(AwaitingContainer::orderId).toList());
		Map<UUID, UserAccount> people = accounts.getAccounts(page.stream()
			.flatMap(a -> Stream.of(a.userId(), a.taskAssigneeId())).filter(Objects::nonNull).distinct().toList());
		return new ContainerAssignmentQueue(PageResponse.from(page, a -> pending(a, orderNumbers.get(a.orderId()), people)));
	}

	/**
	 * A finance member takes the task of assigning an investment's container, as they must before assigning it.
	 */
	@Transactional
	public ContainerAssignmentQueue.Pending take(UUID actorId, UUID holdingId) {
		return view(holdings.takeContainerTask(actorId, holdingId, clock.instant()));
	}

	/** Gives the task back to the queue, unassigned, for another finance member. */
	@Transactional
	public ContainerAssignmentQueue.Pending release(UUID actorId, UUID holdingId) {
		return view(holdings.releaseContainerTask(actorId, holdingId));
	}

	private ContainerAssignmentQueue.Pending view(AwaitingContainer a) {
		Map<UUID, UserAccount> people = accounts.getAccounts(Stream.of(a.userId(), a.taskAssigneeId())
			.filter(Objects::nonNull).distinct().toList());
		return pending(a, orders.orderNumbers(List.of(a.orderId())).get(a.orderId()), people);
	}

	private static ContainerAssignmentQueue.Pending pending(AwaitingContainer a, String orderNumber,
			Map<UUID, UserAccount> people) {
		UserAccount investor = people.get(a.userId());
		UserAccount assignee = a.taskAssigneeId() == null ? null : people.get(a.taskAssigneeId());
		return new ContainerAssignmentQueue.Pending(a.holdingId(), a.orderId(), orderNumber, a.userId(),
				investor == null ? null : (investor.firstName() + " " + investor.lastName()).strip(),
				investor == null ? null : investor.email(), a.productCode(), a.productTitle(), a.containerType(),
				MoneyResponse.from(a.amount()), a.tenureMonths(), a.paidAt(),
				assignee == null ? null : new Assignee(assignee.id(),
						(assignee.firstName() + " " + assignee.lastName()).strip(), a.taskAssignedAt()));
	}

	/** A new container number for finance to assign, instead of typing one in. */
	public String generateContainerNumber() {
		return containers.generateNumber();
	}

	@Transactional(readOnly = true)
	public long pendingCount() {
		return holdings.awaitingContainerCount();
	}

	/**
	 * Records the container finance typed in as the investor's and issues its lease agreement. The lease and hourly
	 * rent start once the investor accepts the agreement ({@link AgreementAcceptanceService}).
	 */
	@Transactional
	public void assign(UUID actorId, UUID holdingId, String containerNumber, String currentLocation) {
		HoldingResponse assigned = holdings.assignContainer(actorId, holdingId, containerNumber, currentLocation,
				clock.instant());
		UUID userId = holdings.ownerOf(holdingId);
		LeaseAgreementResponse agreement = agreements.issue(actorId, assigned, userId,
				orders.orderNumbers(List.of(assigned.orderId())).get(assigned.orderId()));
		notifications.notify(userId, "CONTAINER_ASSIGNED", "Container " + assigned.container().containerNumber()
				+ " is now yours", assigned.productCode() + ": accept your lease agreement " + agreement.agreementNumber()
				+ " to start your lease. Rent is credited to your wallet " + credits.current().label() + " from then on.",
				"/portfolio/" + holdingId + "/agreement");
	}

	/**
	 * Issues the lease agreement of each of an order's containers that has none: those assigned before agreements were
	 * issued. Containers that already have one keep it.
	 *
	 * @return the order's agreements, old and new
	 */
	@Transactional
	public List<LeaseAgreementResponse> issueMissingAgreements(UUID actorId, UUID orderId) {
		OrderResponse order = orders.detail(orderId);
		return holdings.forOrder(orderId).stream()
			.filter(h -> h.container() != null)
			.map(h -> agreements.issue(actorId, h, order.userId(), order.orderNumber()))
			.toList();
	}

}

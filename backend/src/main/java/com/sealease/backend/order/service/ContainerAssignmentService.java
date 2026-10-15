package com.sealease.backend.order.service;

import com.sealease.backend.common.api.Assignee;
import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.earning.service.PayoutService;
import com.sealease.backend.investment.dto.PayoutTerms;
import com.sealease.backend.investment.service.HoldingService;
import com.sealease.backend.investment.service.HoldingService.AwaitingContainer;
import com.sealease.backend.notification.service.NotificationService;
import com.sealease.backend.order.dto.ContainerAssignmentQueue;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.service.UserAccountService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Finance assigns a container to each paid investment (CONTAINER_ASSIGN) by typing its number, once a finance member
 * has taken the task (only they may then assign it). Assigning leases the
 * container to the investor and starts the lease and the monthly payout schedule that day; the investor is notified.
 */
@Service
public class ContainerAssignmentService {

	private final HoldingService holdings;
	private final PayoutService payouts;
	private final OrderService orders;
	private final UserAccountService accounts;
	private final NotificationService notifications;
	private final Clock clock;

	public ContainerAssignmentService(HoldingService holdings, PayoutService payouts,
			OrderService orders, UserAccountService accounts, NotificationService notifications, Clock clock) {
		this.holdings = holdings;
		this.payouts = payouts;
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

	@Transactional(readOnly = true)
	public long pendingCount() {
		return holdings.awaitingContainerCount();
	}

	/** Records and leases the container finance typed in; the lease and hourly rent start at the next full hour. */
	@Transactional
	public void assign(UUID actorId, UUID holdingId, String containerNumber, String currentLocation) {
		Instant now = clock.instant();
		PayoutTerms terms = holdings.assignContainer(actorId, holdingId, containerNumber, currentLocation,
				payouts.leaseStartAfter(now), now);
		payouts.schedule(terms);
		notifications.notify(terms.userId(), "CONTAINER_ASSIGNED", "Container " + terms.containerNumber() + " is now yours",
				terms.productCode() + ": your lease starts now and rent is credited to your wallet every hour.", "/portfolio");
	}

}

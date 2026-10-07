package com.sealease.backend.investment.service;

import com.sealease.backend.container.service.ContainerUsage;
import com.sealease.backend.investment.repository.InvestmentProductRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Tells the container module whether a container backs a live offering. A separate bean (not
 * ProductService) so the container module's dependency on it does not form a cycle.
 */
@Component
public class LiveOfferingCheck implements ContainerUsage {

	private final InvestmentProductRepository products;

	public LiveOfferingCheck(InvestmentProductRepository products) {
		this.products = products;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean hasLiveOffering(UUID containerId) {
		return products.existsLiveForContainer(containerId);
	}

}

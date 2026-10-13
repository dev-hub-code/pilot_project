package com.sealease.backend.container.service;

import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.container.dto.ContainerSummary;
import com.sealease.backend.container.repository.ContainerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Read access to containers for other modules. Containers are recorded when finance assigns them. */
@Service
public class ContainerService {

	private final ContainerRepository containers;

	public ContainerService(ContainerRepository containers) {
		this.containers = containers;
	}

	@Transactional(readOnly = true)
	public ContainerSummary summary(UUID containerId) {
		return containers.findById(containerId).map(ContainerSummary::from)
			.orElseThrow(() -> new ResourceNotFoundException("Container", containerId));
	}

	@Transactional(readOnly = true)
	public Map<UUID, ContainerSummary> summaries(Collection<UUID> containerIds) {
		Map<UUID, ContainerSummary> result = new LinkedHashMap<>();
		// Investments awaiting a container have none yet.
		containers.findAllById(containerIds.stream().filter(Objects::nonNull).distinct().toList())
			.forEach(c -> result.put(c.getId(), ContainerSummary.from(c)));
		return result;
	}

}

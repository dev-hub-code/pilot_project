package com.sealease.backend.container.service;

import java.util.UUID;

/**
 * Answers whether a container currently backs a live investment offering. Implemented by the
 * investment module so that the container module does not depend on it.
 */
public interface ContainerUsage {

	boolean hasLiveOffering(UUID containerId);

}

package com.sealease.backend.container.dto;

import com.sealease.backend.container.entity.ContainerStatus;
import com.sealease.backend.container.entity.ContainerType;

public record ContainerSearchCriteria(String q, ContainerStatus status, ContainerType containerType) {
}

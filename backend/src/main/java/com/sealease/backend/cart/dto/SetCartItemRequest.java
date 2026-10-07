package com.sealease.backend.cart.dto;

import com.sealease.backend.investment.service.PlanRules;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** How many containers of the plan to buy. */
public record SetCartItemRequest(@NotNull @Min(1) @Max(PlanRules.MAX_CONTAINERS_PER_LINE) Integer quantity) {
}

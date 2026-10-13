package com.sealease.backend.reporting.controller;

import com.sealease.backend.reporting.dto.PlatformStats;
import com.sealease.backend.reporting.service.PlatformStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public, unauthenticated: the platform's track record shown on the website. */
@RestController
public class PublicStatsController {

	private final PlatformStatsService stats;

	public PublicStatsController(PlatformStatsService stats) {
		this.stats = stats;
	}

	@GetMapping("/api/v1/public/stats")
	public PlatformStats stats() {
		return stats.current();
	}

}

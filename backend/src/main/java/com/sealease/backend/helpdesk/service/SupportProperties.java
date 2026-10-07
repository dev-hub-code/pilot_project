package com.sealease.backend.helpdesk.service;

import com.sealease.backend.helpdesk.entity.TicketPriority;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * First-response targets per priority: a ticket with no staff reply after its target is overdue.
 */
@ConfigurationProperties(prefix = "app.support")
public record SupportProperties(@DefaultValue ResponseTargets responseTargets) {

	public record ResponseTargets(@DefaultValue("48h") Duration low, @DefaultValue("24h") Duration normal,
			@DefaultValue("8h") Duration high, @DefaultValue("2h") Duration urgent) {

		public Duration of(TicketPriority priority) {
			return switch (priority) {
				case LOW -> low;
				case NORMAL -> normal;
				case HIGH -> high;
				case URGENT -> urgent;
			};
		}

	}

}

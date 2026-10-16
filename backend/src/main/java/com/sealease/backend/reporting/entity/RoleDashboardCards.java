package com.sealease.backend.reporting.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The dashboard cards a role shows; a role without one shows every card. */
@Entity
@Table(name = "role_dashboard_cards")
public class RoleDashboardCards {

	@Id
	@Column(name = "role_id")
	private UUID roleId;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "cards", nullable = false)
	private String[] cards;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "updated_by")
	private UUID updatedBy;

	protected RoleDashboardCards() {
	}

	public RoleDashboardCards(UUID roleId) {
		this.roleId = roleId;
	}

	public void choose(List<String> cards, UUID actorId, Instant now) {
		this.cards = cards.toArray(String[]::new);
		this.updatedBy = actorId;
		this.updatedAt = now;
	}

	public List<String> getCards() {
		return List.of(cards);
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}

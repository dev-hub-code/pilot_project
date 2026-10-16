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

/** One employee's overview: the cards shown, in order, and the ones they took off. */
@Entity
@Table(name = "dashboard_layouts")
public class DashboardLayout {

	@Id
	@Column(name = "user_id")
	private UUID userId;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "cards", nullable = false)
	private String[] cards;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "hidden", nullable = false)
	private String[] hidden;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected DashboardLayout() {
	}

	public DashboardLayout(UUID userId) {
		this.userId = userId;
	}

	public void arrange(List<String> cards, List<String> hidden, Instant now) {
		this.cards = cards.toArray(String[]::new);
		this.hidden = hidden.toArray(String[]::new);
		this.updatedAt = now;
	}

	public List<String> getCards() {
		return List.of(cards);
	}

	public List<String> getHidden() {
		return List.of(hidden);
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}

package com.sealease.backend.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Base for mutable aggregates: time-ordered UUID primary key, optimistic-lock version and
 * creation/modification timestamps.
 *
 * <p>Immutable ledger-style records (earnings, financial transactions, audit logs) should NOT
 * extend this class - they have no version or update timestamp, and the database rejects updates
 * to them (see {@code forbid_mutation()} in V1).
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.TIME)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public UUID getId() {
		return id;
	}

	public long getVersion() {
		return version;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	/** Identity equality that is safe with Hibernate proxies and transient instances. */
	@Override
	public final boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof BaseEntity other)) {
			return false;
		}
		return id != null && effectiveClass(this) == effectiveClass(other) && id.equals(other.getId());
	}

	@Override
	public final int hashCode() {
		return effectiveClass(this).hashCode();
	}

	private static Class<?> effectiveClass(Object o) {
		return o instanceof HibernateProxy proxy
				? proxy.getHibernateLazyInitializer().getPersistentClass()
				: o.getClass();
	}

}

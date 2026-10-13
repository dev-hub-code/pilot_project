package com.sealease.backend.container.entity;

import com.sealease.backend.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** A physical shipping container, which investors buy under a plan and lease out for its tenure. */
@Entity
@Table(name = "containers")
public class Container extends BaseEntity {

	@Column(name = "container_number", nullable = false, length = 20, updatable = false)
	private String containerNumber;

	@Enumerated(EnumType.STRING)
	@Column(name = "container_type", nullable = false, length = 30, updatable = false)
	private ContainerType containerType;

	@Enumerated(EnumType.STRING)
	@Column(name = "condition", length = 20)
	private ContainerCondition condition;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private ContainerStatus status;

	@Column(name = "capacity_cbm", precision = 8, scale = 2)
	private BigDecimal capacityCbm;

	@Column(name = "max_gross_kg")
	private Integer maxGrossKg;

	@Column(name = "tare_kg")
	private Integer tareKg;

	@Column(name = "manufacture_year")
	private Short manufactureYear;

	@Column(name = "manufacturer", length = 100)
	private String manufacturer;

	@Column(name = "current_location", length = 120)
	private String currentLocation;

	@Column(name = "location_country", length = 2)
	private String locationCountry;

	@Column(name = "acquisition_cost", precision = 19, scale = 4)
	private BigDecimal acquisitionCost;

	@Column(name = "acquisition_currency", length = 3)
	private String acquisitionCurrency;

	@Column(name = "notes", length = 1000)
	private String notes;

	@Column(name = "status_reason", length = 500)
	private String statusReason;

	@Column(name = "reserved_order_id")
	private UUID reservedOrderId;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	protected Container() {
	}

	public Container(String containerNumber, ContainerType containerType, UUID createdBy) {
		this.containerNumber = containerNumber;
		this.containerType = containerType;
		this.createdBy = createdBy;
		this.status = ContainerStatus.AVAILABLE;
	}

	/** Where the container is, as finance recorded it when assigning it. */
	public void locatedAt(String currentLocation) {
		this.currentLocation = currentLocation;
	}

	/** Held for an order awaiting payment. */
	public void reserve(UUID orderId) {
		requireStatus(ContainerStatus.AVAILABLE);
		this.status = ContainerStatus.RESERVED;
		this.reservedOrderId = orderId;
	}

	/** The order lapsed or was cancelled: back to inventory. */
	public void releaseReservation() {
		requireStatus(ContainerStatus.RESERVED);
		this.status = ContainerStatus.AVAILABLE;
		this.reservedOrderId = null;
	}

	/** The order was paid: the container is allocated to the investor and leased. */
	public void lease() {
		requireStatus(ContainerStatus.RESERVED);
		this.status = ContainerStatus.ON_LEASE;
		this.reservedOrderId = null;
	}

	/** Finance assigned this available container to a paid investment: it is leased from now. */
	public void assign() {
		requireStatus(ContainerStatus.AVAILABLE);
		this.status = ContainerStatus.ON_LEASE;
	}

	/** The lease ran its tenure: back to inventory. */
	public void endLease() {
		requireStatus(ContainerStatus.ON_LEASE);
		this.status = ContainerStatus.AVAILABLE;
	}

	private void requireStatus(ContainerStatus expected) {
		if (status != expected) {
			throw new IllegalStateException("Container " + containerNumber + " is " + status + ", expected " + expected);
		}
	}

	public UUID getReservedOrderId() {
		return reservedOrderId;
	}

	public String getContainerNumber() {
		return containerNumber;
	}

	public ContainerType getContainerType() {
		return containerType;
	}

	public ContainerCondition getCondition() {
		return condition;
	}

	public ContainerStatus getStatus() {
		return status;
	}

	public BigDecimal getCapacityCbm() {
		return capacityCbm;
	}

	public Integer getMaxGrossKg() {
		return maxGrossKg;
	}

	public Integer getTareKg() {
		return tareKg;
	}

	public Short getManufactureYear() {
		return manufactureYear;
	}

	public String getManufacturer() {
		return manufacturer;
	}

	public String getCurrentLocation() {
		return currentLocation;
	}

	public String getLocationCountry() {
		return locationCountry;
	}

	public BigDecimal getAcquisitionCost() {
		return acquisitionCost;
	}

	public String getAcquisitionCurrency() {
		return acquisitionCurrency;
	}

	public String getNotes() {
		return notes;
	}

	public String getStatusReason() {
		return statusReason;
	}

}

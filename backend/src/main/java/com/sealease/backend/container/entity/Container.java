package com.sealease.backend.container.entity;

import com.sealease.backend.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** A physical shipping container that can back an investment offering. */
@Entity
@Table(name = "containers")
public class Container extends BaseEntity {

	@Column(name = "container_number", nullable = false, length = 11, updatable = false)
	private String containerNumber;

	@Enumerated(EnumType.STRING)
	@Column(name = "container_type", nullable = false, length = 30, updatable = false)
	private ContainerType containerType;

	@Enumerated(EnumType.STRING)
	@Column(name = "condition", nullable = false, length = 20)
	private ContainerCondition condition;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private ContainerStatus status;

	@Column(name = "capacity_cbm", nullable = false, precision = 8, scale = 2)
	private BigDecimal capacityCbm;

	@Column(name = "max_gross_kg", nullable = false)
	private int maxGrossKg;

	@Column(name = "tare_kg", nullable = false)
	private int tareKg;

	@Column(name = "manufacture_year", nullable = false)
	private short manufactureYear;

	@Column(name = "manufacturer", length = 100)
	private String manufacturer;

	@Column(name = "current_location", nullable = false, length = 120)
	private String currentLocation;

	@Column(name = "location_country", nullable = false, length = 2)
	private String locationCountry;

	@Column(name = "acquisition_cost", precision = 19, scale = 4)
	private BigDecimal acquisitionCost;

	@Column(name = "acquisition_currency", length = 3)
	private String acquisitionCurrency;

	@Column(name = "notes", length = 1000)
	private String notes;

	@Column(name = "status_reason", length = 500)
	private String statusReason;

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

	public void describe(ContainerCondition condition, BigDecimal capacityCbm, int maxGrossKg, int tareKg,
			short manufactureYear, String manufacturer, String currentLocation, String locationCountry,
			BigDecimal acquisitionCost, String acquisitionCurrency, String notes) {
		this.condition = condition;
		this.capacityCbm = capacityCbm;
		this.maxGrossKg = maxGrossKg;
		this.tareKg = tareKg;
		this.manufactureYear = manufactureYear;
		this.manufacturer = manufacturer;
		this.currentLocation = currentLocation;
		this.locationCountry = locationCountry;
		this.acquisitionCost = acquisitionCost;
		this.acquisitionCurrency = acquisitionCurrency;
		this.notes = notes;
	}

	public void changeStatus(ContainerStatus status, String reason) {
		this.status = status;
		this.statusReason = reason;
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

	public int getMaxGrossKg() {
		return maxGrossKg;
	}

	public int getTareKg() {
		return tareKg;
	}

	public short getManufactureYear() {
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

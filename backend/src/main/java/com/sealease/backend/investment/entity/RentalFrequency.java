package com.sealease.backend.investment.entity;

public enum RentalFrequency {

	MONTHLY(12),
	QUARTERLY(4);

	private final int periodsPerYear;

	RentalFrequency(int periodsPerYear) {
		this.periodsPerYear = periodsPerYear;
	}

	public int periodsPerYear() {
		return periodsPerYear;
	}

	public int monthsPerPeriod() {
		return 12 / periodsPerYear;
	}

}

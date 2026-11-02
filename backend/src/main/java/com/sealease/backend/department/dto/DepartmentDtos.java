package com.sealease.backend.department.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** What the department and staff endpoints send and receive. */
public final class DepartmentDtos {

	private DepartmentDtos() {
	}

	public record CreateDepartmentRequest(
			@NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$") String code,
			@NotBlank @Size(max = 120) String name,
			@Size(max = 255) String description) {
	}

	public record UpdateDepartmentRequest(
			@NotBlank @Size(max = 120) String name,
			@Size(max = 255) String description,
			long version) {
	}

	public record SetHeadRequest(UUID userId) {
	}

	public record AssignStaffRequest(@NotNull UUID userId, boolean primary) {
	}

	public record DepartmentSummary(UUID id, String code, String name, String description, boolean active,
			UUID headUserId, String headName, long employees, long roles, long version) {
	}

	public record Employee(UUID userId, String name, String email, boolean primary, boolean head, Instant assignedAt) {
	}

	public record DepartmentRole(UUID id, String name, String description, long employees) {
	}

	public record HistoryEntry(String action, String detail, String actorName, Instant at) {
	}

	public record DepartmentDetail(DepartmentSummary department, List<Employee> employees, List<DepartmentRole> roles,
			List<HistoryEntry> history) {
	}

	public record StaffDepartment(UUID id, String code, String name, boolean primary) {
	}

	public record StaffMember(UUID id, String name, String email, String status, List<String> roles,
			List<StaffDepartment> departments) {
	}

}

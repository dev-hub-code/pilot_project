package com.sealease.backend.reporting.repository;

import com.sealease.backend.reporting.entity.DashboardLayout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DashboardLayoutRepository extends JpaRepository<DashboardLayout, UUID> {
}

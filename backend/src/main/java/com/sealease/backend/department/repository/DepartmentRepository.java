package com.sealease.backend.department.repository;

import com.sealease.backend.department.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

	boolean existsByCode(String code);

}

package com.sealease.backend.permission.repository;

import com.sealease.backend.permission.entity.Permission;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

	List<Permission> findByCodeIn(Collection<String> codes);

	List<Permission> findAll(Sort sort);

}

package com.sealease.backend.role.repository;

import com.sealease.backend.role.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

	@EntityGraph(attributePaths = "permissions")
	Optional<Role> findWithPermissionsById(UUID id);

	@EntityGraph(attributePaths = "permissions")
	Optional<Role> findByName(String name);

	@EntityGraph(attributePaths = "permissions")
	List<Role> findByNameIn(Collection<String> names);

	boolean existsByName(String name);

	@EntityGraph(attributePaths = "permissions")
	Page<Role> findAllBy(Pageable pageable);

}

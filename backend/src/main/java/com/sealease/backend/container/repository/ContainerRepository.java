package com.sealease.backend.container.repository;

import com.sealease.backend.container.entity.Container;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ContainerRepository extends JpaRepository<Container, UUID>, JpaSpecificationExecutor<Container> {

	boolean existsByContainerNumber(String containerNumber);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Container c where c.id = :id")
	Optional<Container> findByIdForUpdate(@Param("id") UUID id);

}

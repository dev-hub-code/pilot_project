package com.sealease.backend.container.repository;

import com.sealease.backend.container.entity.Container;
import com.sealease.backend.container.entity.ContainerStatus;
import com.sealease.backend.container.entity.ContainerType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContainerRepository extends JpaRepository<Container, UUID>, JpaSpecificationExecutor<Container> {

	boolean existsByContainerNumber(String containerNumber);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Container c where c.id = :id")
	Optional<Container> findByIdForUpdate(@Param("id") UUID id);

	/**
	 * Locks up to {@code limit} available containers of a type, oldest first. Rows another
	 * transaction is reserving are skipped rather than waited for, so concurrent checkouts never
	 * block each other or take the same container.
	 */
	@Query(value = """
			select * from containers
			where container_type = :type and status = 'AVAILABLE'
			order by created_at, id
			limit :limit
			for update skip locked
			""", nativeQuery = true)
	List<Container> lockAvailable(@Param("type") String type, @Param("limit") int limit);

	List<Container> findByReservedOrderId(UUID orderId);

	long countByContainerTypeAndStatus(ContainerType type, ContainerStatus status);

	@Query("select c.containerType, count(c) from Container c where c.status = :status group by c.containerType")
	List<Object[]> countByTypeWithStatus(@Param("status") ContainerStatus status);

}

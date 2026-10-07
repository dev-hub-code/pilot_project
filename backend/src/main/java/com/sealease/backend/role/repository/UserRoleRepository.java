package com.sealease.backend.role.repository;

import com.sealease.backend.role.entity.UserRole;
import com.sealease.backend.role.entity.UserRoleId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

	@EntityGraph(attributePaths = { "role", "role.permissions" })
	List<UserRole> findByIdUserId(UUID userId);

	@Query("select distinct ur.id.userId from UserRole ur where ur.id.roleId = :roleId")
	List<UUID> findUserIdsByRoleId(@Param("roleId") UUID roleId);

	@Query(value = """
			SELECT DISTINCT ur.user_id FROM user_roles ur
			JOIN role_permissions rp ON rp.role_id = ur.role_id
			JOIN permissions p ON p.id = rp.permission_id
			WHERE p.code = :code
			""", nativeQuery = true)
	List<UUID> findUserIdsWithPermission(@Param("code") String permissionCode);

	boolean existsByIdRoleId(UUID roleId);

	@Query("select count(distinct ur.id.userId) from UserRole ur where ur.role.name = :roleName")
	long countUsersWithRole(@Param("roleName") String roleName);

	@Query("select count(ur) > 0 from UserRole ur where ur.role.name = :roleName")
	boolean existsByRoleName(@Param("roleName") String roleName);

}

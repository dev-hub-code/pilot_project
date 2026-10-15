package com.sealease.backend.kyc.repository;

import com.sealease.backend.kyc.entity.KycFollowUp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface KycFollowUpRepository extends JpaRepository<KycFollowUp, UUID> {

	/**
	 * Active investors registered at or before {@code cutoff} who have not submitted their identity documents (a
	 * draft with one of them does not count) and have no follow-up ticket yet; oldest first.
	 */
	@Query(value = """
			SELECT u.id FROM users u
			JOIN user_profiles p ON p.user_id = u.id
			WHERE u.status = 'ACTIVE' AND u.created_at <= :cutoff AND p.kyc_status = 'NOT_SUBMITTED'
			AND NOT EXISTS (SELECT 1 FROM kyc_follow_ups f WHERE f.user_id = u.id)
			AND EXISTS (SELECT 1 FROM user_roles ur
			            JOIN role_permissions rp ON rp.role_id = ur.role_id
			            JOIN permissions pm ON pm.id = rp.permission_id
			            WHERE ur.user_id = u.id AND pm.code = 'INVESTOR_PORTAL')
			ORDER BY u.created_at
			LIMIT :limit
			""", nativeQuery = true)
	List<UUID> findDue(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

}

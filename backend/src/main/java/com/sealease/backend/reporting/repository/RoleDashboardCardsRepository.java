package com.sealease.backend.reporting.repository;

import com.sealease.backend.reporting.entity.RoleDashboardCards;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RoleDashboardCardsRepository extends JpaRepository<RoleDashboardCards, UUID> {

	/**
	 * For each of the user's roles, its chosen cards joined by commas, or null when the role has not chosen (shows
	 * every card).
	 */
	@Query(value = """
			SELECT array_to_string(c.cards, ',') FROM user_roles ur
			LEFT JOIN role_dashboard_cards c ON c.role_id = ur.role_id
			WHERE ur.user_id = :userId
			""", nativeQuery = true)
	List<String> cardsOfRolesOf(@Param("userId") UUID userId);

}

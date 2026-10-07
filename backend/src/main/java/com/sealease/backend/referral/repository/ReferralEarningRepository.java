package com.sealease.backend.referral.repository;

import com.sealease.backend.referral.entity.ReferralEarning;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ReferralEarningRepository
		extends JpaRepository<ReferralEarning, UUID>, JpaSpecificationExecutor<ReferralEarning> {

	Page<ReferralEarning> findByBeneficiaryUserIdOrderByCreatedAtDescIdDesc(UUID beneficiaryUserId, Pageable pageable);

	/** Commission earned by one upline, per referred investor and currency. */
	@Query("""
			select new com.sealease.backend.referral.repository.ReferralTotal(e.sourceUserId, e.currency, sum(e.amount))
			from ReferralEarning e where e.beneficiaryUserId = :userId
			group by e.sourceUserId, e.currency
			""")
	List<ReferralTotal> totalsBySource(@Param("userId") UUID beneficiaryUserId);

}

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

	/**
	 * Commission per referred holding, calendar month (UTC) and level, adding up its hourly rows; latest first.
	 * Either filter may be null.
	 */
	@Query(value = """
			select source_holding_id as holdingId, to_char(created_at at time zone 'UTC', 'YYYY-MM') as month, level,
			       source_user_id as sourceUserId, beneficiary_user_id as beneficiaryUserId, product_id as productId,
			       currency, sum(base_amount) as baseAmount, max(rate_percent) as ratePercent, sum(amount) as amount,
			       max(created_at) as lastCreditedAt
			from referral_earnings
			where (cast(:beneficiary as uuid) is null or beneficiary_user_id = cast(:beneficiary as uuid))
			  and (cast(:source as uuid) is null or source_user_id = cast(:source as uuid))
			group by 1, 2, level, source_user_id, beneficiary_user_id, product_id, currency
			order by max(created_at) desc, 1, level
			""", countQuery = """
			select count(*) from (
			    select 1 from referral_earnings
			    where (cast(:beneficiary as uuid) is null or beneficiary_user_id = cast(:beneficiary as uuid))
			      and (cast(:source as uuid) is null or source_user_id = cast(:source as uuid))
			    group by source_holding_id, to_char(created_at at time zone 'UTC', 'YYYY-MM'), level, beneficiary_user_id,
			             currency) g
			""", nativeQuery = true)
	Page<ReferralEarningGroup> grouped(@Param("beneficiary") UUID beneficiaryUserId, @Param("source") UUID sourceUserId,
			Pageable pageable);

	/** Commission earned by one upline, per referred investor and currency. */
	@Query("""
			select new com.sealease.backend.referral.repository.ReferralTotal(e.sourceUserId, e.currency, sum(e.amount))
			from ReferralEarning e where e.beneficiaryUserId = :userId
			group by e.sourceUserId, e.currency
			""")
	List<ReferralTotal> totalsBySource(@Param("userId") UUID beneficiaryUserId);

	/** Commission earned by one upline per calendar month (UTC), level and currency, latest month first. */
	@Query(value = """
			select to_char(created_at at time zone 'UTC', 'YYYY-MM') as month, level, currency, sum(amount) as amount
			from referral_earnings where beneficiary_user_id = :userId
			group by 1, 2, 3
			order by 1 desc, 3, 2
			""", nativeQuery = true)
	List<MonthlyLevelTotal> monthlyByLevel(@Param("userId") UUID beneficiaryUserId);

}

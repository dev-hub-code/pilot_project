package com.sealease.backend.payment.repository;

import com.sealease.backend.payment.entity.CompanyBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CompanyBankAccountRepository extends JpaRepository<CompanyBankAccount, UUID> {

	List<CompanyBankAccount> findAllByOrderByActiveDescCreatedAtAsc();

	List<CompanyBankAccount> findByActiveTrueOrderByCreatedAtAsc();

	boolean existsByIfscCodeAndAccountNumberAndIdNot(String ifscCode, String accountNumber, UUID id);

	boolean existsByIfscCodeAndAccountNumber(String ifscCode, String accountNumber);

	boolean existsByActiveTrue();

}

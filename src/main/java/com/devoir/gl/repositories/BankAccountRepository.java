package com.devoir.gl.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.devoir.gl.entities.BankAccount;
import com.devoir.gl.entities.BankAccount.AccountType;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.List;

@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {
	
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT ba FROM BankAccount ba WHERE ba.iban = ?1")
	Optional<BankAccount> findByIbanForUpdate(String iban);
	
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT ba FROM BankAccount ba WHERE ba.accountNumber = ?1")
	Optional<BankAccount> findByAccountNumberForUpdate(String accountNumber);
	
	Optional<BankAccount> findByIban(String iban);
	
	Optional<BankAccount> findByAccountNumber(String accountNumber);
	
	List<BankAccount> findByUserId(Long userId);
	
	List<BankAccount> findByBankId(Long bankId);
	
	List<BankAccount> findByBankIdAndAccountType(Long bankId, AccountType accountType);
	
	List<BankAccount> findByLinkedBankId(Long linkedBankId);
	
	List<BankAccount> findByUserIdAndAccountType(Long userId, AccountType accountType);
	
}

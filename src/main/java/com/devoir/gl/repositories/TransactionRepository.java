package com.devoir.gl.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devoir.gl.entities.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
	
    List<Transaction> findBySenderAccount_AccountNumberOrderByTimestampDesc(String accountNumber);

    List<Transaction> findByReceiverAccount_AccountNumberOrderByTimestampDesc(String accountNumber);
	
}

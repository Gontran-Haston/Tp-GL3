package com.devoir.gl.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.devoir.gl.entities.Account;
import com.devoir.gl.entities.Transaction;
import com.devoir.gl.repositories.AccountRepository;
import com.devoir.gl.repositories.TransactionRepository;
import com.devoir.gl.utils.TransactionResDTO;
import com.devoir.gl.utils.TransactionType;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransactionService {
	
	@Autowired
    private AccountRepository accountRepository;
	
	@Autowired
    private TransactionRepository transactionRepository;
    
    @Transactional
	public void transfer(String fromAcc, String toAcc, BigDecimal amount) {

        Account from = accountRepository.findByAccountNumberForUpdate(fromAcc)
                .orElseThrow(() -> new RuntimeException("Compte source introuvable"));

        Account to = accountRepository.findByAccountNumberForUpdate(toAcc)
                .orElseThrow(() -> new RuntimeException("Compte destination introuvable"));

        if (from.getAccountNumber().equals(to.getAccountNumber())) {
            throw new RuntimeException("Auto-transfert interdit");
        }

        if (from.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Fonds insuffisants");
        }

        // update balances
        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));

        String ref = UUID.randomUUID().toString();

        // RETRAIT
        Transaction debit = new Transaction();
        debit.setAmount(amount);
        debit.setTimestamp(LocalDateTime.now());
        debit.setType(TransactionType.WITHDRAW);
        debit.setAccount(from);
        debit.setReference(ref);
        debit.setDescription("Transfert vers " + toAcc);

        // DEPOT
        Transaction credit = new Transaction();
        credit.setAmount(amount);
        credit.setTimestamp(LocalDateTime.now());
        credit.setType(TransactionType.DEPOSIT);
        credit.setAccount(to);
        credit.setReference(ref);
        credit.setDescription("Transfert depuis " + fromAcc);

        transactionRepository.save(debit);
        transactionRepository.save(credit);
    }
    
    @Async("taskExecutor")
	public List<TransactionResDTO> getHistory(String accountNumber) {
		return transactionRepository
				.findByAccount_AccountNumberOrderByTimestampDesc(accountNumber)
				.stream()
				.map(tx -> {
					TransactionResDTO res = new TransactionResDTO();
					res.setAmount(tx.getAmount());
					res.setType(tx.getType().name());
					res.setTimestamp(tx.getTimestamp());
					res.setDescription(tx.getDescription());
					res.setReference(tx.getReference());
					return res;
				})
				.toList();
	}
    
    private Transaction createTransaction(
            Account account,
            BigDecimal amount,
            TransactionType type,
            String description,
            String reference) {

        Transaction tx = new Transaction();
        
        tx.setAccount(account);
        tx.setAmount(amount);
        tx.setType(type);
        tx.setTimestamp(LocalDateTime.now());
        tx.setDescription(description);
        tx.setReference(reference);

        return tx;
    }
    
    @Transactional
	public void depot(String accountNumber, BigDecimal amount) {
	
	    Account acc = accountRepository.findByAccountNumberForUpdate(accountNumber)
	            .orElseThrow(() -> new RuntimeException("Compte introuvable"));
	    
	    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
	        throw new RuntimeException("Montant invalide");
	    }else {
	    	
	    	acc.setBalance(acc.getBalance().add(amount));
	    	
	    	Transaction tx = createTransaction(
	    			acc,
	    			amount,
	    			TransactionType.DEPOSIT,
	    			"Dépôt externe",
	    			UUID.randomUUID().toString()
	    			);
	    	transactionRepository.save(tx);
	    }
	
	}
    
    @Transactional
	public void retrait(String accountNumber, BigDecimal amount) {
	
	    Account acc = accountRepository.findByAccountNumberForUpdate(accountNumber)
	            .orElseThrow(() -> new RuntimeException("Compte introuvable"));
	
	    if (acc.getBalance().compareTo(amount) < 0) {
	        throw new RuntimeException("Fonds insuffisants");
	    }
	
	    acc.setBalance(acc.getBalance().subtract(amount));
	
	    Transaction tx = createTransaction(
	            acc,
	            amount,
	            TransactionType.WITHDRAW,
	            "Retrait externe",
	            UUID.randomUUID().toString()
	    );
	
	    transactionRepository.save(tx);
	}
    
}

package com.devoir.gl.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.devoir.gl.entities.BankAccount;
import com.devoir.gl.entities.Transaction;
import com.devoir.gl.repositories.BankAccountRepository;
import com.devoir.gl.repositories.TransactionRepository;
import com.devoir.gl.utils.TransactionResDTO;
import com.devoir.gl.utils.TransactionType;

import io.github.resilience4j.retry.annotation.Retry;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransactionService {

	@Autowired
	private BankAccountRepository bankAccountRepository;

	@Autowired
	private TransactionRepository transactionRepository;

	@Retry(name = "dbRetry", fallbackMethod = "fallbackTransfer")
	@Transactional
	public void transfer(String fromAcc, String toAcc, BigDecimal amount) {
		BankAccount from = bankAccountRepository.findByAccountNumberForUpdate(fromAcc)
				.orElseThrow(() -> new RuntimeException("Compte source introuvable"));
		BankAccount to = bankAccountRepository.findByAccountNumberForUpdate(toAcc)
				.orElseThrow(() -> new RuntimeException("Compte destination introuvable"));

		if (from.getAccountNumber().equals(to.getAccountNumber())) {
			throw new RuntimeException("Auto-transfert interdit");
		}
		if (amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new RuntimeException("Montant invalide");
		}
		if (from.getBalance().compareTo(amount) < 0) {
			throw new RuntimeException("Fonds insuffisants");
		}

		from.setBalance(from.getBalance().subtract(amount));
		to.setBalance(to.getBalance().add(amount));

		String ref = UUID.randomUUID().toString();
		Transaction debit = createTransaction(from, to, amount, TransactionType.WITHDRAW, "Transfert vers " + toAcc, ref);
		Transaction credit = createTransaction(from, to, amount, TransactionType.DEPOSIT, "Transfert depuis " + fromAcc, ref);

		bankAccountRepository.save(from);
		bankAccountRepository.save(to);
		transactionRepository.save(debit);
		transactionRepository.save(credit);
	}

	public List<TransactionResDTO> getHistory(String accountNumber) {
		return Stream.concat(
				transactionRepository.findBySenderAccount_AccountNumberOrderByTimestampDesc(accountNumber).stream(),
				transactionRepository.findByReceiverAccount_AccountNumberOrderByTimestampDesc(accountNumber).stream())
				.sorted(Comparator.comparing(Transaction::getTimestamp).reversed())
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

	@Transactional
	public void depot(String accountNumber, BigDecimal amount) {
		BankAccount acc = bankAccountRepository.findByAccountNumberForUpdate(accountNumber)
				.orElseThrow(() -> new RuntimeException("Compte introuvable"));
		if (amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new RuntimeException("Montant invalide");
		}
		acc.setBalance(acc.getBalance().add(amount));
		bankAccountRepository.save(acc);
		transactionRepository.save(createTransaction(null, acc, amount, TransactionType.DEPOSIT, "Dépôt externe",
				UUID.randomUUID().toString()));
	}

	@Transactional
	public void retrait(String accountNumber, BigDecimal amount) {
		BankAccount acc = bankAccountRepository.findByAccountNumberForUpdate(accountNumber)
				.orElseThrow(() -> new RuntimeException("Compte introuvable"));
		if (amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new RuntimeException("Montant invalide");
		}
		if (acc.getBalance().compareTo(amount) < 0) {
			throw new RuntimeException("Fonds insuffisants");
		}
		acc.setBalance(acc.getBalance().subtract(amount));
		bankAccountRepository.save(acc);
		transactionRepository.save(createTransaction(acc, null, amount, TransactionType.WITHDRAW, "Retrait externe",
				UUID.randomUUID().toString()));
	}

	private Transaction createTransaction(BankAccount sender, BankAccount receiver, BigDecimal amount, TransactionType type,
			String description, String reference) {
		Transaction tx = new Transaction();
		tx.setSenderAccount(sender);
		tx.setReceiverAccount(receiver);
		tx.setAmount(amount);
		tx.setType(type);
		tx.setTimestamp(LocalDateTime.now());
		tx.setDescription(description);
		tx.setReference(reference);
		return tx;
	}

	public void fallbackTransfer(String fromAcc, String toAcc, BigDecimal amount, Throwable t) {
		throw new RuntimeException(
				"Erreur lors du virement de " + fromAcc + " vers " + toAcc + " - Veuillez réessayer: " + t.getMessage(),
				t);
	}
}

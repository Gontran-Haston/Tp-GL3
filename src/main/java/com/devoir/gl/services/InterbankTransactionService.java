package com.devoir.gl.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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

/**
 * Service amélioré pour gérer les transactions inter-banques
 */
@Service
@RequiredArgsConstructor
public class InterbankTransactionService {

	@Autowired
	private BankAccountRepository bankAccountRepository;

	@Autowired
	private TransactionRepository transactionRepository;

	/**
	 * Effectue un virement inter-banques entre deux comptes
	 */
	@Retry(name = "dbRetry", fallbackMethod = "fallbackInterbankTransfer")
	@Transactional
	public void transferInterbank(String fromIban, String toIban, BigDecimal amount) {

		BankAccount from = bankAccountRepository.findByIbanForUpdate(fromIban)
				.orElseThrow(() -> new RuntimeException("Compte source introuvable: " + fromIban));

		BankAccount to = bankAccountRepository.findByIbanForUpdate(toIban)
				.orElseThrow(() -> new RuntimeException("Compte destinataire introuvable: " + toIban));

		if (from.getIban().equals(to.getIban())) {
			throw new RuntimeException("Auto-transfert interdit");
		}

		if (from.getBalance().compareTo(amount) < 0) {
			throw new RuntimeException("Fonds insuffisants sur le compte " + from.getIban());
		}

		if (amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new RuntimeException("Montant invalide");
		}

		// Mise à jour des soldes
		from.setBalance(from.getBalance().subtract(amount));
		to.setBalance(to.getBalance().add(amount));

		String referenceId = UUID.randomUUID().toString();

		// Transaction de débit sur le compte source
		Transaction debit = new Transaction();
		debit.setAmount(amount);
		debit.setTimestamp(LocalDateTime.now());
		debit.setType(TransactionType.WITHDRAW);
		debit.setSenderAccount(from);
		debit.setReceiverAccount(to);
		debit.setReference(referenceId);
		debit.setDescription("Virement inter-banques vers " + to.getIban());
		debit.setTransferStatus("COMPLETED");

		// Transaction de crédit sur le compte destinataire
		Transaction credit = new Transaction();
		credit.setAmount(amount);
		credit.setTimestamp(LocalDateTime.now());
		credit.setType(TransactionType.DEPOSIT);
		credit.setSenderAccount(from);
		credit.setReceiverAccount(to);
		credit.setReference(referenceId);
		credit.setDescription("Virement inter-banques depuis " + from.getIban());
		credit.setTransferStatus("COMPLETED");

		bankAccountRepository.save(from);
		bankAccountRepository.save(to);
		transactionRepository.save(debit);
		transactionRepository.save(credit);
	}

	/**
	 * Effectue un virement intra-banque (ancien système)
	 */
	@Retry(name = "dbRetry", fallbackMethod = "fallbackIntrabankTransfer")
	@Transactional
	public void transferIntrabank(String fromAccountNumber, String toAccountNumber, BigDecimal amount) {

		BankAccount from = bankAccountRepository.findByAccountNumberForUpdate(fromAccountNumber)
				.orElseThrow(() -> new RuntimeException("Compte source introuvable"));

		BankAccount to = bankAccountRepository.findByAccountNumberForUpdate(toAccountNumber)
				.orElseThrow(() -> new RuntimeException("Compte destinataire introuvable"));

		if (from.getAccountNumber().equals(to.getAccountNumber())) {
			throw new RuntimeException("Auto-transfert interdit");
		}

		if (from.getBalance().compareTo(amount) < 0) {
			throw new RuntimeException("Fonds insuffisants");
		}

		// Mise à jour des soldes
		from.setBalance(from.getBalance().subtract(amount));
		to.setBalance(to.getBalance().add(amount));

		String referenceId = UUID.randomUUID().toString();

		// Débit
		Transaction debit = new Transaction();
		debit.setAmount(amount);
		debit.setTimestamp(LocalDateTime.now());
		debit.setType(TransactionType.WITHDRAW);
		debit.setSenderAccount(from);
		debit.setReceiverAccount(to);
		debit.setReference(referenceId);
		debit.setDescription("Transfert vers " + toAccountNumber);

		// Crédit
		Transaction credit = new Transaction();
		credit.setAmount(amount);
		credit.setTimestamp(LocalDateTime.now());
		credit.setType(TransactionType.DEPOSIT);
		credit.setSenderAccount(from);
		credit.setReceiverAccount(to);
		credit.setReference(referenceId);
		credit.setDescription("Transfert depuis " + fromAccountNumber);

		bankAccountRepository.save(from);
		bankAccountRepository.save(to);
		transactionRepository.save(debit);
		transactionRepository.save(credit);
	}

	/**
	 * Effectue un dépôt sur un compte
	 */
	@Transactional
	public void deposit(String iban, BigDecimal amount) {

		BankAccount acc = bankAccountRepository.findByIbanForUpdate(iban)
				.orElseThrow(() -> new RuntimeException("Compte introuvable"));

		if (amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new RuntimeException("Montant invalide");
		}

		acc.setBalance(acc.getBalance().add(amount));

		Transaction tx = new Transaction();
		tx.setAmount(amount);
		tx.setTimestamp(LocalDateTime.now());
		tx.setType(TransactionType.DEPOSIT);
		tx.setReceiverAccount(acc);
		tx.setReference(UUID.randomUUID().toString());
		tx.setDescription("Dépôt externe");

		bankAccountRepository.save(acc);
		transactionRepository.save(tx);
	}

	/**
	 * Effectue un retrait depuis un compte
	 */
	@Transactional
	public void withdraw(String iban, BigDecimal amount) {

		BankAccount acc = bankAccountRepository.findByIbanForUpdate(iban)
				.orElseThrow(() -> new RuntimeException("Compte introuvable"));

		if (acc.getBalance().compareTo(amount) < 0) {
			throw new RuntimeException("Fonds insuffisants");
		}

		if (amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new RuntimeException("Montant invalide");
		}

		acc.setBalance(acc.getBalance().subtract(amount));

		Transaction tx = new Transaction();
		tx.setAmount(amount);
		tx.setTimestamp(LocalDateTime.now());
		tx.setType(TransactionType.WITHDRAW);
		tx.setSenderAccount(acc);
		tx.setReference(UUID.randomUUID().toString());
		tx.setDescription("Retrait externe");

		bankAccountRepository.save(acc);
		transactionRepository.save(tx);
	}

	/**
	 * Récupère l'historique des transactions d'un compte
	 */
	public List<TransactionResDTO> getHistory(String iban) {
		BankAccount account = bankAccountRepository.findByIban(iban)
				.orElseThrow(() -> new RuntimeException("Compte introuvable"));

		return transactionRepository
				.findAll().stream()
				.filter(tx -> (tx.getSenderAccount() != null && tx.getSenderAccount().getId().equals(account.getId()))
						|| (tx.getReceiverAccount() != null && tx.getReceiverAccount().getId().equals(account.getId())))
				.sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
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

	public void fallbackInterbankTransfer(String fromIban, String toIban, BigDecimal amount, Throwable t) {
		throw new RuntimeException(
				"Erreur lors du virement inter-banques de " + fromIban + " vers " + toIban + ": " + t.getMessage(),
				t);
	}

	public void fallbackIntrabankTransfer(String fromAccountNumber, String toAccountNumber, BigDecimal amount, Throwable t) {
		throw new RuntimeException(
				"Erreur lors du virement intra-banque de " + fromAccountNumber + " vers " + toAccountNumber + ": "
						+ t.getMessage(),
				t);
	}
}

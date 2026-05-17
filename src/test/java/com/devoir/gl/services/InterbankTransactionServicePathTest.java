package com.devoir.gl.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.devoir.gl.entities.Bank;
import com.devoir.gl.entities.BankAccount;
import com.devoir.gl.entities.BankAccount.AccountType;
import com.devoir.gl.entities.User;
import com.devoir.gl.repositories.BankAccountRepository;
import com.devoir.gl.repositories.BankRepository;
import com.devoir.gl.repositories.TransactionRepository;
import com.devoir.gl.repositories.UserRepository;
import com.devoir.gl.utils.TransactionResDTO;

@SpringBootTest
@Transactional
class InterbankTransactionServicePathTest {

	@Autowired
	private InterbankTransactionService service;

	@Autowired
	private BankRepository bankRepository;

	@Autowired
	private BankAccountRepository bankAccountRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TransactionRepository transactionRepository;

	private Bank bankA;
	private Bank bankB;
	private User userA;
	private User userB;

	@BeforeEach
	void setUp() {
		transactionRepository.deleteAll();
		bankAccountRepository.deleteAll();
		userRepository.deleteAll();
		bankRepository.deleteAll();

		bankA = new Bank();
		bankA.setName("Bank A " + System.nanoTime());
		bankA.setSwiftCode(("BA" + System.nanoTime()).substring(0, 8));
		bankA.setCountry("France");
		bankA.setIbanPrefix("FR");
		bankA = bankRepository.save(bankA);

		bankB = new Bank();
		bankB.setName("Bank B " + System.nanoTime());
		bankB.setSwiftCode(("BB" + System.nanoTime()).substring(0, 8));
		bankB.setCountry("Germany");
		bankB.setIbanPrefix("DE");
		bankB = bankRepository.save(bankB);

		userA = new User();
		userA.setFirst_name("Alice");
		userA.setLast_name("A");
		userA.setEmail("alice-" + System.nanoTime() + "@test.com");
		userA = userRepository.save(userA);

		userB = new User();
		userB.setFirst_name("Bob");
		userB.setLast_name("B");
		userB.setEmail("bob-" + System.nanoTime() + "@test.com");
		userB = userRepository.save(userB);
	}

	private BankAccount createAccount(Bank bank, User user, String accNo, String iban, BigDecimal balance) {
		BankAccount acc = new BankAccount();
		acc.setAccountType(AccountType.CLIENT);
		acc.setBank(bank);
		acc.setUser(user);
		acc.setAccountNumber(accNo);
		acc.setIban(iban);
		acc.setBalance(balance);
		return bankAccountRepository.save(acc);
	}

	@Test
	void pathInterbankSuccess() {
		BankAccount from = createAccount(bankA, userA, "ACC-A-1", "FRA-IBAN-1", new BigDecimal("1000"));
		BankAccount to = createAccount(bankB, userB, "ACC-B-1", "DEB-IBAN-1", new BigDecimal("100"));

		service.transferInterbank(from.getIban(), to.getIban(), new BigDecimal("250"));

		BankAccount fromAfter = bankAccountRepository.findById(from.getId()).orElseThrow();
		BankAccount toAfter = bankAccountRepository.findById(to.getId()).orElseThrow();
		assertEquals(0, new BigDecimal("750").compareTo(fromAfter.getBalance()));
		assertEquals(0, new BigDecimal("350").compareTo(toAfter.getBalance()));
	}

	@Test
	void pathInterbankFromMissing() {
		BankAccount to = createAccount(bankB, userB, "ACC-B-2", "DEB-IBAN-2", new BigDecimal("100"));
		RuntimeException ex = assertThrows(RuntimeException.class,
				() -> service.transferInterbank("MISSING-IBAN", to.getIban(), BigDecimal.TEN));
		assertTrue(ex.getMessage().contains("Compte source introuvable"));
	}

	@Test
	void pathInterbankToMissing() {
		BankAccount from = createAccount(bankA, userA, "ACC-A-2", "FRA-IBAN-2", new BigDecimal("100"));
		RuntimeException ex = assertThrows(RuntimeException.class,
				() -> service.transferInterbank(from.getIban(), "MISSING-IBAN", BigDecimal.TEN));
		assertTrue(ex.getMessage().contains("Compte destinataire introuvable"));
	}

	@Test
	void pathInterbankAutoTransferForbidden() {
		BankAccount from = createAccount(bankA, userA, "ACC-A-3", "FRA-IBAN-3", new BigDecimal("100"));
		RuntimeException ex = assertThrows(RuntimeException.class,
				() -> service.transferInterbank(from.getIban(), from.getIban(), BigDecimal.ONE));
		assertTrue(ex.getMessage().contains("Auto-transfert interdit"));
	}

	@Test
	void pathInterbankInsufficientFunds() {
		BankAccount from = createAccount(bankA, userA, "ACC-A-4", "FRA-IBAN-4", new BigDecimal("10"));
		BankAccount to = createAccount(bankB, userB, "ACC-B-4", "DEB-IBAN-4", new BigDecimal("0"));
		RuntimeException ex = assertThrows(RuntimeException.class,
				() -> service.transferInterbank(from.getIban(), to.getIban(), new BigDecimal("50")));
		assertTrue(ex.getMessage().contains("Fonds insuffisants"));
	}

	@Test
	void pathInterbankInvalidAmount() {
		BankAccount from = createAccount(bankA, userA, "ACC-A-5", "FRA-IBAN-5", new BigDecimal("100"));
		BankAccount to = createAccount(bankB, userB, "ACC-B-5", "DEB-IBAN-5", new BigDecimal("0"));
		RuntimeException ex = assertThrows(RuntimeException.class,
				() -> service.transferInterbank(from.getIban(), to.getIban(), BigDecimal.ZERO));
		assertTrue(ex.getMessage().contains("Montant invalide"));
	}

	@Test
	void pathIntrabankSuccess() {
		BankAccount from = createAccount(bankA, userA, "ACC-I-1", "FRI-IBAN-1", new BigDecimal("300"));
		BankAccount to = createAccount(bankA, userB, "ACC-I-2", "FRI-IBAN-2", new BigDecimal("200"));
		service.transferIntrabank(from.getAccountNumber(), to.getAccountNumber(), new BigDecimal("120"));
		assertEquals(0, new BigDecimal("180").compareTo(bankAccountRepository.findById(from.getId()).orElseThrow().getBalance()));
		assertEquals(0, new BigDecimal("320").compareTo(bankAccountRepository.findById(to.getId()).orElseThrow().getBalance()));
	}

	@Test
	void pathDepositWithdrawAndHistory() {
		BankAccount acc = createAccount(bankA, userA, "ACC-H-1", "FRH-IBAN-1", new BigDecimal("500"));
		service.deposit(acc.getIban(), new BigDecimal("100"));
		service.withdraw(acc.getIban(), new BigDecimal("50"));

		List<TransactionResDTO> history = service.getHistory(acc.getIban());
		assertFalse(history.isEmpty());
		assertEquals(2, history.size());
		assertEquals(0, new BigDecimal("550").compareTo(bankAccountRepository.findById(acc.getId()).orElseThrow().getBalance()));
	}

	@Test
	void pathDepositMissingAccount() {
		RuntimeException ex = assertThrows(RuntimeException.class, () -> service.deposit("UNKNOWN", BigDecimal.ONE));
		assertEquals("Compte introuvable", ex.getMessage());
	}

	@Test
	void pathWithdrawInvalidAmount() {
		BankAccount acc = createAccount(bankA, userA, "ACC-W-1", "FRW-IBAN-1", new BigDecimal("50"));
		RuntimeException ex = assertThrows(RuntimeException.class, () -> service.withdraw(acc.getIban(), BigDecimal.ZERO));
		assertEquals("Montant invalide", ex.getMessage());
	}
}

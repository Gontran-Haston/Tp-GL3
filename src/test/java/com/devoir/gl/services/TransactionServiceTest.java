package com.devoir.gl.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
import com.devoir.gl.services.TransactionService;
import com.devoir.gl.services.UserService;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
class TransactionServiceTest {

	@Autowired
	private TransactionService service;

	@Autowired
	private UserService userService;

	@Autowired
	private BankAccountRepository bankAccountRepository;

	@Autowired
	private BankRepository bankRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TransactionRepository transactionRepository;

	private Bank bank;

	@BeforeEach
	void cleanDatabase() {
		transactionRepository.deleteAll();
		bankAccountRepository.deleteAll();
		userRepository.deleteAll();
		bankRepository.deleteAll();

		bank = new Bank();
		bank.setName("Test Bank " + System.nanoTime());
		bank.setSwiftCode(("TS" + System.nanoTime()).substring(0, 8));
		bank.setCountry("France");
		bank.setIbanPrefix("FR");
		bank = bankRepository.save(bank);
	}

	private BankAccount createAccount(User user, String accountNumber, BigDecimal balance) {
		BankAccount acc = new BankAccount();
		acc.setAccountNumber(accountNumber);
		acc.setIban("FR" + System.nanoTime());
		acc.setBalance(balance);
		acc.setUser(user);
		acc.setBank(bank);
		acc.setAccountType(AccountType.CLIENT);
		return bankAccountRepository.save(acc);
	}

	@Test
	void shouldHandleHighConcurrencyTransfers() throws Exception {
		User user = new User();
		user.setFirst_name("Test");
		user.setLast_name("User");
		user.setEmail("test-" + System.nanoTime() + "@mail.com");
		user = userRepository.save(user);

		BankAccount a = createAccount(user, "ACC-A-" + System.nanoTime(), new BigDecimal("10000"));
		BankAccount b = createAccount(user, "ACC-B-" + System.nanoTime(), new BigDecimal("10000"));

		int operations = 200;
		ExecutorService executor = Executors.newFixedThreadPool(12);
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch endLatch = new CountDownLatch(operations);

		for (int i = 0; i < operations; i++) {
			executor.submit(() -> {
				try {
					startLatch.await();
					service.transfer(a.getAccountNumber(), b.getAccountNumber(), BigDecimal.ONE);
				} catch (Exception ignored) {
				} finally {
					endLatch.countDown();
				}
			});
		}

		startLatch.countDown();
		assertTrue(endLatch.await(15, TimeUnit.SECONDS));
		executor.shutdown();

		BankAccount updatedA = bankAccountRepository.findById(a.getId()).orElseThrow();
		BankAccount updatedB = bankAccountRepository.findById(b.getId()).orElseThrow();
		assertEquals(0, new BigDecimal("20000").compareTo(updatedA.getBalance().add(updatedB.getBalance())));
	}

	@Test
	@Transactional
	void shouldCreateUserWithBankAccount() throws Exception {
		User user = new User();
		user.setFirst_name("Paul");
		user.setLast_name("Zidane");
		user.setEmail("test4-" + System.nanoTime() + "@mail.com");

		User saved = userService.createUser(user).get();
		assertNotNull(saved.getId());

		User loaded = userRepository.findById(saved.getId()).orElseThrow();
		assertNotNull(loaded.getBankAccounts());
		assertFalse(loaded.getBankAccounts().isEmpty());
	}

	@Test
	void shouldDepositAndWithdrawMoneyCorrectly() {
		User user = new User();
		user.setFirst_name("Test");
		user.setLast_name("User");
		user.setEmail("deposit-" + System.nanoTime() + "@test.com");
		user = userRepository.save(user);

		BankAccount acc = createAccount(user, "ACC-DEPOSIT-" + System.nanoTime(), new BigDecimal("1000"));
		service.depot(acc.getAccountNumber(), new BigDecimal("500"));
		service.retrait(acc.getAccountNumber(), new BigDecimal("300"));

		BankAccount updated = bankAccountRepository.findById(acc.getId()).orElseThrow();
		assertEquals(0, new BigDecimal("1200").compareTo(updated.getBalance()));
	}

	@Test
	void shouldFailWithdrawWhenInsufficientFunds() {
		User user = new User();
		user.setFirst_name("Test");
		user.setLast_name("User");
		user.setEmail("fail-" + System.nanoTime() + "@test.com");
		user = userRepository.save(user);

		BankAccount acc = createAccount(user, "ACC-FAIL-" + System.nanoTime(), new BigDecimal("100"));

		RuntimeException ex = assertThrows(RuntimeException.class,
				() -> service.retrait(acc.getAccountNumber(), new BigDecimal("500")));
		assertEquals("Fonds insuffisants", ex.getMessage());
	}
}

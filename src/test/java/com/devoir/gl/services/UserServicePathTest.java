package com.devoir.gl.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.devoir.gl.entities.Bank;
import com.devoir.gl.entities.BankAccount;
import com.devoir.gl.entities.User;
import com.devoir.gl.repositories.BankAccountRepository;
import com.devoir.gl.repositories.BankRepository;
import com.devoir.gl.repositories.TransactionRepository;
import com.devoir.gl.repositories.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class UserServicePathTest {

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private BankRepository bankRepository;

	@Autowired
	private BankAccountRepository bankAccountRepository;

	@Autowired
	private TransactionRepository transactionRepository;

	@BeforeEach
	void cleanAndSeed() {
		transactionRepository.deleteAll();
		bankAccountRepository.deleteAll();
		userRepository.deleteAll();
		bankRepository.deleteAll();
	}

	@Test
	void pathCreateUserWithDefaultBankCreatesAccount() throws Exception {
		Bank bank = new Bank();
		bank.setName("Path Bank");
		bank.setSwiftCode("PTHBFRPP");
		bank.setCountry("France");
		bank.setIbanPrefix("FR");
		bankRepository.save(bank);

		User user = new User();
		user.setFirst_name("Path");
		user.setLast_name("User");
		user.setEmail("path.user." + System.nanoTime() + "@test.com");

		User saved = userService.createUser(user).get();
		assertNotNull(saved.getId());

		List<BankAccount> accounts = bankAccountRepository.findByUserId(saved.getId());
		assertEquals(1, accounts.size());
		assertEquals(BankAccount.AccountType.CLIENT, accounts.get(0).getAccountType());
		assertTrue(accounts.get(0).getIban().startsWith("FR"));
	}

	@Test
	void pathCreateUserWithoutBankDoesNotCreateAccount() throws Exception {
		User user = new User();
		user.setFirst_name("No");
		user.setLast_name("Bank");
		user.setEmail("nobank." + System.nanoTime() + "@test.com");

		User saved = userService.createUser(user).get();
		assertNotNull(saved.getId());

		List<BankAccount> accounts = bankAccountRepository.findByUserId(saved.getId());
		assertTrue(accounts.isEmpty());
	}

	@Test
	void pathCreateUserNullThrows() {
		ExecutionException ex = assertThrows(ExecutionException.class, () -> userService.createUser(null).get());
		assertNotNull(ex.getCause());
		assertEquals("Limite de requêtes atteinte. Veuillez réessayer plus tard.", ex.getCause().getMessage());
	}

	@Test
	void pathFindAllUsersReturnsExistingUsers() {
		User user = new User();
		user.setFirst_name("Find");
		user.setLast_name("All");
		user.setEmail("findall." + System.nanoTime() + "@test.com");
		userRepository.save(user);

		List<User> users = userService.findAllUsers();
		assertFalse(users.isEmpty());
		assertTrue(users.stream().anyMatch(u -> u.getEmail().equals(user.getEmail())));
	}
}

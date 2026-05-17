package com.devoir.gl.services;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.devoir.gl.entities.Bank;
import com.devoir.gl.entities.BankAccount;
import com.devoir.gl.entities.BankAccount.AccountType;
import com.devoir.gl.entities.BankAccount.AccountSubType;
import com.devoir.gl.entities.User;
import com.devoir.gl.repositories.BankRepository;
import com.devoir.gl.repositories.BankAccountRepository;
import com.devoir.gl.repositories.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class BankAccountSubtypeTest {

	@Autowired
	private BankService bankService;

	@Autowired
	private BankRepository bankRepository;

	@Autowired
	private BankAccountRepository bankAccountRepository;

	@Autowired
	private UserRepository userRepository;

	private Bank bank1;
	private User user1;

	@BeforeEach
	void setUp() {
		// Créer une banque
		bank1 = new Bank();
		bank1.setName("BNP Paribas");
		bank1.setSwiftCode("BNPAPFRP");
		bank1.setCountry("France");
		bank1.setIbanPrefix("FR");
		bank1 = bankRepository.save(bank1);

		// Créer un utilisateur
		user1 = new User();
		user1.setFirst_name("Jean");
		user1.setLast_name("Dupont");
		user1.setEmail("jean.dupont@example.com");
		user1 = userRepository.save(user1);
	}

	@Test
	void testCreateCheckingAccountByDefault() {
		BankAccount account = bankService.createClientAccount(user1.getId(), bank1.getId());

		assertNotNull(account);
		assertEquals(AccountType.CLIENT, account.getAccountType());
		assertEquals(AccountSubType.CHECKING, account.getAccountSubtype());
		assertEquals(user1.getId(), account.getUser().getId());
		assertEquals(bank1.getId(), account.getBank().getId());
		assertEquals(BigDecimal.ZERO, account.getBalance());
	}

	@Test
	void testCreateCheckingAccountExplicitly() {
		BankAccount account = bankService.createClientAccount(user1.getId(), bank1.getId(), AccountSubType.CHECKING);

		assertNotNull(account);
		assertEquals(AccountType.CLIENT, account.getAccountType());
		assertEquals(AccountSubType.CHECKING, account.getAccountSubtype());
		assertEquals(user1.getId(), account.getUser().getId());
		assertEquals(bank1.getId(), account.getBank().getId());
	}

	@Test
	void testCreateSavingsAccount() {
		BankAccount account = bankService.createClientAccount(user1.getId(), bank1.getId(), AccountSubType.SAVINGS);

		assertNotNull(account);
		assertEquals(AccountType.CLIENT, account.getAccountType());
		assertEquals(AccountSubType.SAVINGS, account.getAccountSubtype());
		assertEquals(user1.getId(), account.getUser().getId());
		assertEquals(bank1.getId(), account.getBank().getId());
	}

	@Test
	void testUserCanHaveMultipleAccounts() {
		// Créer un compte courant
		BankAccount checking = bankService.createClientAccount(user1.getId(), bank1.getId(), AccountSubType.CHECKING);
		
		// Créer un compte d'épargne
		BankAccount savings = bankService.createClientAccount(user1.getId(), bank1.getId(), AccountSubType.SAVINGS);

		assertNotNull(checking);
		assertNotNull(savings);
		assertNotEquals(checking.getId(), savings.getId());
		assertEquals(checking.getUser().getId(), savings.getUser().getId());
		assertEquals(AccountSubType.CHECKING, checking.getAccountSubtype());
		assertEquals(AccountSubType.SAVINGS, savings.getAccountSubtype());
	}

	@Test
	void testGetSavingsAccounts() {
		// Créer plusieurs comptes
		BankAccount checking = bankService.createClientAccount(user1.getId(), bank1.getId(), AccountSubType.CHECKING);
		BankAccount savings = bankService.createClientAccount(user1.getId(), bank1.getId(), AccountSubType.SAVINGS);
		
		User user2 = new User();
		user2.setFirst_name("Marie");
		user2.setLast_name("Martin");
		user2.setEmail("marie.martin@example.com");
		user2 = userRepository.save(user2);
		
		BankAccount savings2 = bankService.createClientAccount(user2.getId(), bank1.getId(), AccountSubType.SAVINGS);

		// Récupérer tous les comptes d'épargne
		List<BankAccount> savingsAccounts = bankService.getSavingsAccounts(bank1.getId());

		assertEquals(2, savingsAccounts.size());
		assertTrue(savingsAccounts.stream().allMatch(acc -> acc.getAccountSubtype().equals(AccountSubType.SAVINGS)));
	}

	@Test
	void testGetCheckingAccounts() {
		// Créer plusieurs comptes
		BankAccount checking1 = bankService.createClientAccount(user1.getId(), bank1.getId(), AccountSubType.CHECKING);
		BankAccount savings = bankService.createClientAccount(user1.getId(), bank1.getId(), AccountSubType.SAVINGS);
		
		User user2 = new User();
		user2.setFirst_name("Marie");
		user2.setLast_name("Martin");
		user2.setEmail("marie.martin@example.com");
		user2 = userRepository.save(user2);
		
		BankAccount checking2 = bankService.createClientAccount(user2.getId(), bank1.getId(), AccountSubType.CHECKING);

		// Récupérer tous les comptes courants
		List<BankAccount> checkingAccounts = bankService.getCheckingAccounts(bank1.getId());

		assertEquals(2, checkingAccounts.size());
		assertTrue(checkingAccounts.stream().allMatch(acc -> acc.getAccountSubtype().equals(AccountSubType.CHECKING)));
	}

	@Test
	void testTransferBetweenAccountSubtypes() {
		// Créer un compte courant et un compte d'épargne pour le même utilisateur
		BankAccount checking = bankService.createClientAccount(user1.getId(), bank1.getId(), AccountSubType.CHECKING);
		BankAccount savings = bankService.createClientAccount(user1.getId(), bank1.getId(), AccountSubType.SAVINGS);

		// Ajouter des fonds au compte courant
		checking.setBalance(BigDecimal.valueOf(1000));
		bankAccountRepository.save(checking);

		// Les deux comptes doivent être dans la même banque et avoir le même propriétaire
		assertEquals(checking.getBank().getId(), savings.getBank().getId());
		assertEquals(checking.getUser().getId(), savings.getUser().getId());
	}

	@Test
	void testAccountSubtypeIsPersisted() {
		BankAccount savings = bankService.createClientAccount(user1.getId(), bank1.getId(), AccountSubType.SAVINGS);
		Long accountId = savings.getId();

		// Récupérer depuis la base de données
		BankAccount retrieved = bankAccountRepository.findById(accountId).get();

		assertEquals(AccountSubType.SAVINGS, retrieved.getAccountSubtype());
		assertEquals(AccountType.CLIENT, retrieved.getAccountType());
	}

	@Test
	void testMultipleUsersMultipleAccountSubtypes() {
		User user2 = new User();
		user2.setFirst_name("Marie");
		user2.setLast_name("Martin");
		user2.setEmail("marie.martin@example.com");
		user2 = userRepository.save(user2);

		// User1: 1 checking + 1 savings
		BankAccount user1Checking = bankService.createClientAccount(user1.getId(), bank1.getId(), AccountSubType.CHECKING);
		BankAccount user1Savings = bankService.createClientAccount(user1.getId(), bank1.getId(), AccountSubType.SAVINGS);

		// User2: 1 checking + 2 savings
		BankAccount user2Checking = bankService.createClientAccount(user2.getId(), bank1.getId(), AccountSubType.CHECKING);
		BankAccount user2Savings1 = bankService.createClientAccount(user2.getId(), bank1.getId(), AccountSubType.SAVINGS);
		BankAccount user2Savings2 = bankService.createClientAccount(user2.getId(), bank1.getId(), AccountSubType.SAVINGS);

		// Vérifier les comptes d'épargne
		List<BankAccount> allSavings = bankService.getSavingsAccounts(bank1.getId());
		assertEquals(3, allSavings.size());

		// Vérifier les comptes courants
		List<BankAccount> allChecking = bankService.getCheckingAccounts(bank1.getId());
		assertEquals(2, allChecking.size());

		// Vérifier les comptes clients totaux
		List<BankAccount> allClientAccounts = bankService.getClientAccounts(bank1.getId());
		assertEquals(5, allClientAccounts.size());
	}
}

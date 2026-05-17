package com.devoir.gl.services;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.devoir.gl.entities.Bank;
import com.devoir.gl.entities.BankAccount;
import com.devoir.gl.entities.BankAccount.AccountSubType;
import com.devoir.gl.entities.User;
import com.devoir.gl.repositories.BankRepository;
import com.devoir.gl.repositories.BankAccountRepository;
import com.devoir.gl.repositories.TransactionRepository;
import com.devoir.gl.repositories.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class BankAccountSubtypePathTest {

	@Autowired
	private BankService bankService;

	@Autowired
	private UserService userService;

	@Autowired
	private InterbankTransactionService interbankTransactionService;

	@Autowired
	private BankRepository bankRepository;

	@Autowired
	private BankAccountRepository bankAccountRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TransactionRepository transactionRepository;

	private Bank bank1;
	private Bank bank2;

	@BeforeEach
	void setUp() {
		transactionRepository.deleteAll();
		bankAccountRepository.deleteAll();
		userRepository.deleteAll();
		bankRepository.deleteAll();

		// Créer deux banques
		bank1 = new Bank();
		bank1.setName("BNP Paribas");
		bank1.setSwiftCode("BNPAPFRP");
		bank1.setCountry("France");
		bank1.setIbanPrefix("FR");
		bank1 = bankRepository.save(bank1);

		bank2 = new Bank();
		bank2.setName("Deutsche Bank");
		bank2.setSwiftCode("DEUTDEFF");
		bank2.setCountry("Germany");
		bank2.setIbanPrefix("DE");
		bank2 = bankRepository.save(bank2);
	}

	/**
	 * Path 1: Un utilisateur crée un compte et obtient un compte courant par défaut
	 */
	@Test
	void testPath_UserCreationWithDefaultCheckingAccount() throws Exception {
		// Créer un utilisateur directement (sin UserService async)
		User user = new User();
		user.setFirst_name("Alice");
		user.setLast_name("Dupont");
		user.setEmail("alice.dupont@example.com");
		User createdUser = userRepository.save(user);

		assertNotNull(createdUser);
		assertNotNull(createdUser.getId());

		// Créer un compte courant par défaut
		BankAccount defaultAccount = bankService.createClientAccount(createdUser.getId(), bank1.getId());

		assertNotNull(defaultAccount);
		assertEquals(AccountSubType.CHECKING, defaultAccount.getAccountSubtype());
		
		// Vérifier que le compte est bien lié à l'utilisateur
		List<BankAccount> userAccounts = bankAccountRepository.findByUserId(createdUser.getId());
		assertEquals(1, userAccounts.size());
		assertEquals(AccountSubType.CHECKING, userAccounts.get(0).getAccountSubtype());
	}

	/**
	 * Path 2: Un usuario crée varios comptes (courant + épargne)
	 */
	@Test
	void testPath_UserCreatesCheckingAndSavingsAccounts() throws Exception {
		User user = new User();
		user.setFirst_name("Bob");
		user.setLast_name("Martin");
		user.setEmail("bob.martin@example.com");
		User createdUser = userRepository.save(user);
		Long userId = createdUser.getId();

		// Créer un compte courant
		BankAccount checking = bankService.createClientAccount(userId, bank1.getId(), AccountSubType.CHECKING);
		
		// Créer un compte d'épargne
		BankAccount savings = bankService.createClientAccount(userId, bank1.getId(), AccountSubType.SAVINGS);

		assertNotNull(checking);
		assertNotNull(savings);
		assertEquals(AccountSubType.CHECKING, checking.getAccountSubtype());
		assertEquals(AccountSubType.SAVINGS, savings.getAccountSubtype());

		// Vérifier que l'utilisateur a exactement 2 comptes créés
		List<BankAccount> userAccounts = bankAccountRepository.findByUserId(userId);
		assertEquals(2, userAccounts.size());
	}

	/**
	 * Path 3: Un utilisateur effectue des dépôts dans ses différents comptes
	 */
	@Test
	void testPath_UserDepositsIntoCheckingAndSavingsAccounts() throws Exception {
		User user = new User();
		user.setFirst_name("Charlie");
		user.setLast_name("Renaud");
		user.setEmail("charlie.renaud@example.com");
		User createdUser = userRepository.save(user);
		Long userId = createdUser.getId();

		// Créer les comptes
		BankAccount checking = bankService.createClientAccount(userId, bank1.getId(), AccountSubType.CHECKING);
		BankAccount savings = bankService.createClientAccount(userId, bank1.getId(), AccountSubType.SAVINGS);

		// Dépôt dans le compte courant
		interbankTransactionService.deposit(checking.getIban(), BigDecimal.valueOf(2000));
		
		// Dépôt dans le compte d'épargne
		interbankTransactionService.deposit(savings.getIban(), BigDecimal.valueOf(5000));

		// Vérifier les soldes
		BankAccount checkingAfter = bankAccountRepository.findById(checking.getId()).get();
		BankAccount savingsAfter = bankAccountRepository.findById(savings.getId()).get();

		assertEquals(BigDecimal.valueOf(2000), checkingAfter.getBalance());
		assertEquals(BigDecimal.valueOf(5000), savingsAfter.getBalance());
	}

	/**
	 * Path 4: Transfert entre compte courant et compte d'épargne du même utilisateur
	 */
	@Test
	void testPath_TransferBetweenCheckingAndSavings() throws Exception {
		User user = new User();
		user.setFirst_name("Diana");
		user.setLast_name("Morin");
		user.setEmail("diana.morin@example.com");
		User createdUser = userRepository.save(user);
		Long userId = createdUser.getId();

		// Créer et remplir un compte courant
		BankAccount checking = bankService.createClientAccount(userId, bank1.getId(), AccountSubType.CHECKING);
		checking.setBalance(BigDecimal.valueOf(3000));
		bankAccountRepository.save(checking);

		// Créer un compte d'épargne vide
		BankAccount savings = bankService.createClientAccount(userId, bank1.getId(), AccountSubType.SAVINGS);

		// Effectuer un transfert (simulé)
		BigDecimal transferAmount = BigDecimal.valueOf(1000);
		checking.setBalance(checking.getBalance().subtract(transferAmount));
		savings.setBalance(savings.getBalance().add(transferAmount));
		bankAccountRepository.save(checking);
		bankAccountRepository.save(savings);

		// Vérifier
		BankAccount checkingFinal = bankAccountRepository.findById(checking.getId()).get();
		BankAccount savingsFinal = bankAccountRepository.findById(savings.getId()).get();

		assertEquals(BigDecimal.valueOf(2000), checkingFinal.getBalance());
		assertEquals(BigDecimal.valueOf(1000), savingsFinal.getBalance());
	}

	/**
	 * Path 5: Multiple utilisateurs avec différents comptes
	 */
	@Test
	void testPath_MultipleUsersWithMultipleAccountTypes() throws Exception {
		// Créer 3 utilisateurs
		User user1 = new User();
		user1.setFirst_name("Emma");
		user1.setLast_name("Petit");
		user1.setEmail("emma.petit@example.com");
		User createdUser1 = userRepository.save(user1);

		User user2 = new User();
		user2.setFirst_name("Frank");
		user2.setLast_name("Grand");
		user2.setEmail("frank.grand@example.com");
		User createdUser2 = userRepository.save(user2);

		User user3 = new User();
		user3.setFirst_name("Grace");
		user3.setLast_name("Blonde");
		user3.setEmail("grace.blonde@example.com");
		User createdUser3 = userRepository.save(user3);

		// User1: 2 comptes courants + 1 épargne
		bankService.createClientAccount(createdUser1.getId(), bank1.getId(), AccountSubType.CHECKING);
		bankService.createClientAccount(createdUser1.getId(), bank2.getId(), AccountSubType.CHECKING);
		bankService.createClientAccount(createdUser1.getId(), bank1.getId(), AccountSubType.SAVINGS);

		// User2: 1 courant + 2 épargnes
		bankService.createClientAccount(createdUser2.getId(), bank1.getId(), AccountSubType.CHECKING);
		bankService.createClientAccount(createdUser2.getId(), bank1.getId(), AccountSubType.SAVINGS);
		bankService.createClientAccount(createdUser2.getId(), bank2.getId(), AccountSubType.SAVINGS);

		// User3: 1 courant + 1 épargne
		bankService.createClientAccount(createdUser3.getId(), bank2.getId(), AccountSubType.CHECKING);
		bankService.createClientAccount(createdUser3.getId(), bank2.getId(), AccountSubType.SAVINGS);

		// Vérifier les comptes
		List<BankAccount> user1Accounts = bankAccountRepository.findByUserId(createdUser1.getId());
		List<BankAccount> user2Accounts = bankAccountRepository.findByUserId(createdUser2.getId());
		List<BankAccount> user3Accounts = bankAccountRepository.findByUserId(createdUser3.getId());

		assertEquals(3, user1Accounts.size());
		assertEquals(3, user2Accounts.size());
		assertEquals(2, user3Accounts.size());

		// Vérifier les comptes courants et d'épargne par banque
		List<BankAccount> bank1Savings = bankService.getSavingsAccounts(bank1.getId());
		List<BankAccount> bank1Checking = bankService.getCheckingAccounts(bank1.getId());

		assertEquals(2, bank1Savings.size());
		assertEquals(2, bank1Checking.size());
	}

	/**
	 * Path 6: Vérifier que les comptes inter-banques ne sont pas affectés
	 */
	@Test
	void testPath_InterbankAccountsNotAffected() {
		// Créer un compte inter-banque
		BankAccount interbankAcc = bankService.createInterbankAccount(bank1.getId(), bank2.getId());

		assertNotNull(interbankAcc);
		// Les comptes inter-banques ne doivent pas avoir de sous-type
		assertNull(interbankAcc.getAccountSubtype());
		assertNull(interbankAcc.getUser());
	}

	/**
	 * Path 7: Un utilisateur accède à tous ses comptes dans toutes les banques
	 */
	@Test
	void testPath_UserAccessesAccountsAcrossBanks() throws Exception {
		User user = new User();
		user.setFirst_name("Henry");
		user.setLast_name("Chen");
		user.setEmail("henry.chen@example.com");
		User createdUser = userRepository.save(user);
		Long userId = createdUser.getId();

		// Créer des comptes dans bank1
		BankAccount bank1Checking = bankService.createClientAccount(userId, bank1.getId(), AccountSubType.CHECKING);
		BankAccount bank1Savings = bankService.createClientAccount(userId, bank1.getId(), AccountSubType.SAVINGS);

		// Créer des comptes dans bank2
		BankAccount bank2Checking = bankService.createClientAccount(userId, bank2.getId(), AccountSubType.CHECKING);
		BankAccount bank2Savings = bankService.createClientAccount(userId, bank2.getId(), AccountSubType.SAVINGS);

		// L'utilisateur devrait avoir accès à 4 comptes
		List<BankAccount> allUserAccounts = bankAccountRepository.findByUserId(userId);
		assertEquals(4, allUserAccounts.size());

		// Vérifier qu'il y a 2 comptes courants et 2 comptes d'épargne
		long checkingCount = allUserAccounts.stream()
				.filter(acc -> acc.getAccountSubtype() == AccountSubType.CHECKING)
				.count();
		long savingsCount = allUserAccounts.stream()
				.filter(acc -> acc.getAccountSubtype() == AccountSubType.SAVINGS)
				.count();

		assertEquals(2, checkingCount);
		assertEquals(2, savingsCount);
	}

	/**
	 * Path 8: Vérifier la persistence et la cohérence des données
	 */
	@Test
	void testPath_DataPersistenceAndConsistency() {
		// Créer un compte avec sous-type
		User user = new User();
		user.setFirst_name("Ivy");
		user.setLast_name("Yen");
		user.setEmail("ivy.yen@example.com");
		user = userRepository.save(user);

		BankAccount savingsAccount = bankService.createClientAccount(user.getId(), bank1.getId(), AccountSubType.SAVINGS);
		Long accountId = savingsAccount.getId();

		// Récupérer du repository
		BankAccount retrieved = bankAccountRepository.findById(accountId).get();

		// Vérifier les données
		assertEquals(AccountSubType.SAVINGS, retrieved.getAccountSubtype());
		assertEquals(user.getId(), retrieved.getUser().getId());
		assertEquals(bank1.getId(), retrieved.getBank().getId());
		assertEquals(BigDecimal.ZERO, retrieved.getBalance());
		assertNotNull(retrieved.getIban());
		assertNotNull(retrieved.getAccountNumber());
	}
}

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
import com.devoir.gl.entities.User;
import com.devoir.gl.repositories.BankRepository;
import com.devoir.gl.repositories.BankAccountRepository;
import com.devoir.gl.repositories.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class BankServiceTest {

	@Autowired
	private BankService bankService;

	@Autowired
	private BankRepository bankRepository;

	@Autowired
	private BankAccountRepository bankAccountRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private InterbankTransactionService transactionService;

	private Bank bank1;
	private Bank bank2;
	private User user1;

	@BeforeEach
	void setUp() {
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

		// Créer un utilisateur
		user1 = new User();
		user1.setFirst_name("Jean");
		user1.setLast_name("Dupont");
		user1.setEmail("jean.dupont@example.com");
		user1 = userRepository.save(user1);
	}

	@Test
		void testCreateBank() {
			List<Bank> banks = bankService.getAllBanks();
			assertTrue(banks.size() >= 2);
			assertTrue(banks.stream().anyMatch(b -> b.getSwiftCode().equals("BNPAPFRP")));
		}

	@Test
	void testCreateClientAccount() {
		BankAccount account = bankService.createClientAccount(user1.getId(), bank1.getId());

		assertNotNull(account);
		assertEquals(AccountType.CLIENT, account.getAccountType());
		assertEquals(user1.getId(), account.getUser().getId());
		assertEquals(bank1.getId(), account.getBank().getId());
		assertEquals(BigDecimal.ZERO, account.getBalance());
		assertTrue(account.getIban().startsWith("FR"));
	}

	@Test
	void testCreateInterbankAccount() {
		BankAccount interbankAccount = bankService.createInterbankAccount(bank1.getId(), bank2.getId());

		assertNotNull(interbankAccount);
		assertEquals(AccountType.INTERBANK, interbankAccount.getAccountType());
		assertEquals(bank2.getId(), interbankAccount.getBank().getId());
		assertEquals(bank1.getId(), interbankAccount.getLinkedBank().getId());
		assertNull(interbankAccount.getUser());
	}

	@Test
	void testCreateInterbankAccountSelfFail() {
		assertThrows(RuntimeException.class, () -> {
			bankService.createInterbankAccount(bank1.getId(), bank1.getId());
		});
	}

	@Test
	void testGetClientAccounts() {
		BankAccount acc1 = bankService.createClientAccount(user1.getId(), bank1.getId());
		User user2 = new User();
		user2.setFirst_name("Marie");
		user2.setLast_name("Martin");
		user2.setEmail("marie.martin@example.com");
		user2 = userRepository.save(user2);
		BankAccount acc2 = bankService.createClientAccount(user2.getId(), bank1.getId());

		List<BankAccount> clientAccounts = bankService.getClientAccounts(bank1.getId());

		assertEquals(2, clientAccounts.size());
		assertTrue(clientAccounts.stream().allMatch(acc -> acc.getAccountType() == AccountType.CLIENT));
	}

	@Test
	void testTransferIntrabank() {
		// Créer deux comptes dans la même banque
		BankAccount from = bankService.createClientAccount(user1.getId(), bank1.getId());
		User user2 = new User();
		user2.setFirst_name("Marie");
		user2.setLast_name("Martin");
		user2.setEmail("marie.martin@example.com");
		user2 = userRepository.save(user2);
		BankAccount to = bankService.createClientAccount(user2.getId(), bank1.getId());

		// Ajouter des fonds au compte source
		from.setBalance(BigDecimal.valueOf(1000));
		bankAccountRepository.save(from);

		// Effectuer le virement
		transactionService.transferIntrabank(
				from.getAccountNumber(),
				to.getAccountNumber(),
				BigDecimal.valueOf(500));

		// Vérifier les soldes
		BankAccount fromAfter = bankAccountRepository.findById(from.getId()).get();
		BankAccount toAfter = bankAccountRepository.findById(to.getId()).get();

		assertEquals(BigDecimal.valueOf(500), fromAfter.getBalance());
		assertEquals(BigDecimal.valueOf(500), toAfter.getBalance());
	}

	@Test
	void testTransferInterbank() {
		// Créer compte client chez bank1
		BankAccount clientAcc = bankService.createClientAccount(user1.getId(), bank1.getId());

		// Créer compte inter-banque (bank1 chez bank2)
		BankAccount interbankAcc = bankService.createInterbankAccount(bank1.getId(), bank2.getId());

		// Ajouter fonds
		clientAcc.setBalance(BigDecimal.valueOf(2000));
		bankAccountRepository.save(clientAcc);

		// Effectuer virement inter-banques
		transactionService.transferInterbank(
				clientAcc.getIban(),
				interbankAcc.getIban(),
				BigDecimal.valueOf(1000));

		// Vérifier
		BankAccount clientAfter = bankAccountRepository.findById(clientAcc.getId()).get();
		BankAccount interbankAfter = bankAccountRepository.findById(interbankAcc.getId()).get();

		assertEquals(BigDecimal.valueOf(1000), clientAfter.getBalance());
		assertEquals(BigDecimal.valueOf(1000), interbankAfter.getBalance());
	}

	@Test
	void testDepositWithdraw() {
		BankAccount account = bankService.createClientAccount(user1.getId(), bank1.getId());

		// Dépôt
		transactionService.deposit(account.getIban(), BigDecimal.valueOf(1000));
		BankAccount afterDeposit = bankAccountRepository.findById(account.getId()).get();
		assertEquals(BigDecimal.valueOf(1000), afterDeposit.getBalance());

		// Retrait
		transactionService.withdraw(account.getIban(), BigDecimal.valueOf(300));
		BankAccount afterWithdraw = bankAccountRepository.findById(account.getId()).get();
		assertEquals(BigDecimal.valueOf(700), afterWithdraw.getBalance());
	}

	@Test
	void testInsufficientFunds() {
		BankAccount account = bankService.createClientAccount(user1.getId(), bank1.getId());
		account.setBalance(BigDecimal.valueOf(100));
		bankAccountRepository.save(account);

		assertThrows(RuntimeException.class, () -> {
			transactionService.withdraw(account.getIban(), BigDecimal.valueOf(200));
		});
	}

	@Test
	void testGetHistory() {
		BankAccount account = bankService.createClientAccount(user1.getId(), bank1.getId());

		transactionService.deposit(account.getIban(), BigDecimal.valueOf(1000));
		transactionService.withdraw(account.getIban(), BigDecimal.valueOf(300));

		var history = transactionService.getHistory(account.getIban());

		assertNotNull(history);
		assertEquals(2, history.size());
	}

	@Test
	void testGetBankBySwiftCode() {
		var foundBank = bankService.getBankBySwiftCode("BNPAPFRP");

		assertTrue(foundBank.isPresent());
		assertEquals("BNP Paribas", foundBank.get().getName());
	}

	@Test
	void testGetAllUserAccounts() {
		BankAccount acc1 = bankService.createClientAccount(user1.getId(), bank1.getId());
		BankAccount acc2 = bankService.createClientAccount(user1.getId(), bank2.getId());

		List<BankAccount> allAccounts = bankService.getAllUserAccounts(user1.getId());

		assertEquals(2, allAccounts.size());
	}
}

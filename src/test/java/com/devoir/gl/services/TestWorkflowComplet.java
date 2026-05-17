package com.devoir.gl.services;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

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
import com.devoir.gl.repositories.BankAccountRepository;
import com.devoir.gl.repositories.BankRepository;
import com.devoir.gl.repositories.TransactionRepository;
import com.devoir.gl.repositories.UserRepository;
import com.devoir.gl.utils.TransactionResDTO;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestWorkflowComplet {

    @Autowired
    private BankService bankService;

    @Autowired
    private UserService userService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private InterbankTransactionService interbankTransactionService;

    @Autowired
    private BankRepository bankRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private Bank bank1;
    private Bank bank2;

    @BeforeEach
    void setup() {

        transactionRepository.deleteAll();
        bankAccountRepository.deleteAll();
        userRepository.deleteAll();
        bankRepository.deleteAll();

        bank1 = new Bank();
        bank1.setName("BNP PARIBAS");
        bank1.setSwiftCode("BNPAFRPP");
        bank1.setCountry("France");
        bank1.setIbanPrefix("FR");

        bank1 = bankRepository.save(bank1);

        bank2 = new Bank();
        bank2.setName("DEUTSCHE BANK");
        bank2.setSwiftCode("DEUTDEFF");
        bank2.setCountry("Germany");
        bank2.setIbanPrefix("DE");

        bank2 = bankRepository.save(bank2);
    }

    /**
     * TEST WORKFLOW COMPLET
     */
    @Test
    void testCompleteBankWorkflow() throws Exception {

        /*CREATION USERS*/

        User user1 = new User();
        user1.setFirst_name("Alice");
        user1.setLast_name("Martin");
        user1.setEmail("alice@test.com");

        User user2 = new User();
        user2.setFirst_name("Bob");
        user2.setLast_name("Dupont");
        user2.setEmail("bob@test.com");

        User createdUser1 = userService.createUser(user1).get();
        User createdUser2 = userService.createUser(user2).get();

        assertNotNull(createdUser1.getId());
        assertNotNull(createdUser2.getId());

        /*CREATION COMPTES*/

        BankAccount aliceChecking =
                bankService.createClientAccount(
                        createdUser1.getId(),
                        bank1.getId(),
                        AccountSubType.CHECKING
                );

        BankAccount aliceSavings =
                bankService.createClientAccount(
                        createdUser1.getId(),
                        bank1.getId(),
                        AccountSubType.SAVINGS
                );

        BankAccount bobChecking =
                bankService.createClientAccount(
                        createdUser2.getId(),
                        bank2.getId(),
                        AccountSubType.CHECKING
                );

        assertNotNull(aliceChecking);
        assertNotNull(aliceSavings);
        assertNotNull(bobChecking);

        /*DEPOTS*/

        interbankTransactionService.deposit(
                aliceChecking.getIban(),
                BigDecimal.valueOf(10000)
        );

        interbankTransactionService.deposit(
                aliceSavings.getIban(),
                BigDecimal.valueOf(5000)
        );

        interbankTransactionService.deposit(
                bobChecking.getIban(),
                BigDecimal.valueOf(2000)
        );

        /*RETRAIT*/

        interbankTransactionService.withdraw(
                bobChecking.getIban(),
                BigDecimal.valueOf(500)
        );

        /*VIREMENT INTRA BANQUE
         * Alice checking -> Alice savings*/

        transactionService.transfer(
                aliceChecking.getAccountNumber(),
                aliceSavings.getAccountNumber(),
                BigDecimal.valueOf(1500)
        );

        /*VIREMENT INTERBANQUE
         * Alice -> Bob*/

        interbankTransactionService.transferInterbank(
                aliceChecking.getIban(),
                bobChecking.getIban(),
                BigDecimal.valueOf(2000)
        );

        /*RELOAD ACCOUNTS*/

        BankAccount aliceCheckingFinal =
                bankAccountRepository.findById(aliceChecking.getId()).get();

        BankAccount aliceSavingsFinal =
                bankAccountRepository.findById(aliceSavings.getId()).get();

        BankAccount bobCheckingFinal =
                bankAccountRepository.findById(bobChecking.getId()).get();

        /*VERIFICATIONS SOLDES*/

        // 10000 - 1500 - 2000 = 6500
        assertEquals(
                0,
                BigDecimal.valueOf(6500)
                        .compareTo(aliceCheckingFinal.getBalance())
        );

        // 5000 + 1500 = 6500
        assertEquals(
                0,
                BigDecimal.valueOf(6500)
                        .compareTo(aliceSavingsFinal.getBalance())
        );

        // 2000 - 500 + 2000 = 3500
        assertEquals(
                0,
                BigDecimal.valueOf(3500)
                        .compareTo(bobCheckingFinal.getBalance())
        );

        /*HISTORIQUE*/

        List<TransactionResDTO> aliceHistory =
                interbankTransactionService.getHistory(
                        aliceChecking.getIban()
                );

        assertFalse(aliceHistory.isEmpty());

        /* VERIFICATION COMPTES*/

        List<BankAccount> aliceAccounts =
                bankService.getAllUserAccounts(createdUser1.getId());

        assertTrue(aliceAccounts.size() >= 3);

        List<BankAccount> bank1Checking =
                bankService.getCheckingAccounts(bank1.getId());

        assertFalse(bank1Checking.isEmpty());
    }

    /**
     * TEST PERFORMANCE / CONCURRENCE
     * 200 REQUETES SIMULTANEES
     */
    @Test
    void testPerformance_200ConcurrentTransfers() throws Exception {

        /*CREATION USERS*/

        User sender = new User();
        sender.setFirst_name("Sender");
        sender.setLast_name("Load");
        sender.setEmail("sender@test.com");

        User receiver = new User();
        receiver.setFirst_name("Receiver");
        receiver.setLast_name("Load");
        receiver.setEmail("receiver@test.com");

        User senderCreated = userService.createUser(sender).get();
        User receiverCreated = userService.createUser(receiver).get();

        /*CREATION COMPTES*/

        BankAccount senderAccount =
                bankService.createClientAccount(
                        senderCreated.getId(),
                        bank1.getId(),
                        AccountSubType.CHECKING
                );

        BankAccount receiverAccount =
                bankService.createClientAccount(
                        receiverCreated.getId(),
                        bank2.getId(),
                        AccountSubType.CHECKING
                );

        /*DEPOT INITIAL*/

        interbankTransactionService.deposit(
                senderAccount.getIban(),
                BigDecimal.valueOf(500000)
        );

        /* EXECUTION CONCURRENTE*/

        int numberOfRequests = 200;

        ExecutorService executor =
                Executors.newFixedThreadPool(50);

        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < numberOfRequests; i++) {

            tasks.add(() -> {

                interbankTransactionService.transferInterbank(
                        senderAccount.getIban(),
                        receiverAccount.getIban(),
                        BigDecimal.valueOf(100)
                );

                return null;
            });
        }

        long start = System.currentTimeMillis();

        List<Future<Void>> futures = executor.invokeAll(tasks);

        for (Future<Void> future : futures) {
            future.get();
        }

        long end = System.currentTimeMillis();

        long duration = end - start;

        executor.shutdown();

        /*
         * =========================================
         * VERIFICATIONS
         * =========================================
         */

        BankAccount senderFinal =
                bankAccountRepository.findById(senderAccount.getId()).get();

        BankAccount receiverFinal =
                bankAccountRepository.findById(receiverAccount.getId()).get();

        /*
         * Sender:
         * 500000 - (200 * 100)
         * = 480000
         */

        assertEquals(
                0,
                BigDecimal.valueOf(480000)
                        .compareTo(senderFinal.getBalance())
        );

        /*
         * Receiver:
         * 200 * 100
         * = 20000
         */

        assertEquals(
                0,
                BigDecimal.valueOf(20000)
                        .compareTo(receiverFinal.getBalance())
        );

        /*
         * 2 transactions par transfert
         * debit + credit
         */

        assertTrue(transactionRepository.findAll().size() >= 400);

        System.out.println(
                "\n=====================================\n" +
                "200 transferts concurrents exécutés\n" +
                "Temps total: " + duration + " ms\n" +
                "=====================================\n"
        );
    }
}
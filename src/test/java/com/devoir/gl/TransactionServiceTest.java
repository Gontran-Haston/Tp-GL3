package com.devoir.gl;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.concurrent.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.devoir.gl.entities.Account;
import com.devoir.gl.entities.User;
import com.devoir.gl.repositories.AccountRepository;
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
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;


    @BeforeEach
    void cleanDatabase() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    // UTILS MONEY ASSERT
    private void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }

    // CONCURRENCY TRANSFER
    @Test
    void shouldHandleHighConcurrencyTransfers() throws Exception {

        User user = new User();
        user.setFirst_name("Test");
        user.setLast_name("User");
        user.setEmail("test-" + System.nanoTime() + "@mail.com");
        user = userRepository.save(user);

        Account a = new Account();
        a.setAccountNumber("ACC-A-" + System.nanoTime());
        a.setBalance(new BigDecimal("10000"));
        a.setUser(user);

        Account b = new Account();
        b.setAccountNumber("ACC-B-" + System.nanoTime());
        b.setBalance(new BigDecimal("10000"));
        b.setUser(user);

        a = accountRepository.save(a);
        b = accountRepository.save(b);

        final String fromAcc = a.getAccountNumber();
        final String toAcc = b.getAccountNumber();

        int operations = 500;
        int threads = 20;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(operations);

        for (int i = 0; i < operations; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    service.transfer(fromAcc, toAcc, BigDecimal.ONE);
                } catch (Exception e) {
                    fail("Erreur transfer: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = endLatch.await(15, TimeUnit.SECONDS);
        assertTrue(completed, "Timeout sur test transfer");

        executor.shutdown();

        Account updatedA = accountRepository.findById(a.getId()).orElseThrow();
        Account updatedB = accountRepository.findById(b.getId()).orElseThrow();

        assertMoney(
                new BigDecimal("20000"),
                updatedA.getBalance().add(updatedB.getBalance())
        );
    }

    // CONCURRENCY WITHDRAW
    @Test
    void shouldHandleConcurrentWithdrawsCorrectly() throws Exception {

        User user = new User();
        user.setFirst_name("Test");
        user.setLast_name("User");
        user.setEmail("withdraw-" + System.nanoTime() + "@mail.com");
        user = userRepository.save(user);

        Account acc = new Account();
        acc.setAccountNumber("ACC-WITHDRAW-" + System.nanoTime());
        acc.setBalance(new BigDecimal("1000"));
        acc.setUser(user);

        acc = accountRepository.save(acc);

        final String accountNumber = acc.getAccountNumber();

        int operations = 10;
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(operations);

        for (int i = 0; i < operations; i++) {
            executor.submit(() -> {
                try {
                    service.retrait(accountNumber, new BigDecimal("50"));
                } catch (Exception e) {
                    fail("Erreur withdraw: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "Timeout withdraw");

        executor.shutdown();

        Account updated = accountRepository.findById(acc.getId()).orElseThrow();

        assertMoney(new BigDecimal("500"), updated.getBalance());
    }

    // CREATE ACCOUNT
    @Test
    void createAccountTest() {

        User user = new User();
        user.setFirst_name("Paul");
        user.setLast_name("Zidane");
        user.setEmail("test3@mail.com");

        Account acc = new Account();
        acc.setUser(user);
        acc.setAccountNumber("ACC-TEST");
        acc.setBalance(BigDecimal.ZERO);

        assertMoney(BigDecimal.ZERO, acc.getBalance());
        assertEquals("ACC-TEST", acc.getAccountNumber());
    }

    // USER WITH ACCOUNT
    @Test
    @Transactional
    void shouldCreateUserWithAccount() throws Exception {

        User user = new User();
        user.setFirst_name("Paul");
        user.setLast_name("Zidane");
        user.setEmail("test4@mail.com");

        User saved = userService.createUser(user).get();

        assertNotNull(saved.getId());

        User loaded = userRepository.findById(saved.getId()).orElseThrow();

        assertNotNull(loaded.getAccounts());
        assertFalse(loaded.getAccounts().isEmpty());
    }

    // DEPOSIT
    @Test
    void shouldDepositMoneyCorrectly() {

        User user = new User();
        user.setFirst_name("Test");
        user.setLast_name("User");
        user.setEmail("deposit@test.com");

        user = userRepository.save(user);

        Account acc = new Account();
        acc.setAccountNumber("ACC-DEPOSIT");
        acc.setBalance(new BigDecimal("1000"));
        acc.setUser(user);

        acc = accountRepository.save(acc);

        service.depot("ACC-DEPOSIT", new BigDecimal("500"));

        Account updated = accountRepository.findById(acc.getId()).orElseThrow();

        assertMoney(new BigDecimal("1500"), updated.getBalance());
    }

    // WITHDRAW
    @Test
    void shouldWithdrawMoneyCorrectly() {

        User user = new User();
        user.setFirst_name("Test");
        user.setLast_name("User");
        user.setEmail("withdraw@test.com");

        user = userRepository.save(user);

        Account acc = new Account();
        acc.setAccountNumber("ACC-WITHDRAW");
        acc.setBalance(new BigDecimal("1000"));
        acc.setUser(user);

        acc = accountRepository.save(acc);

        service.retrait("ACC-WITHDRAW", new BigDecimal("300"));

        Account updated = accountRepository.findById(acc.getId()).orElseThrow();

        assertMoney(new BigDecimal("700"), updated.getBalance());
    }

    // FAIL WITHDRAW
    @Test
    void shouldFailWithdrawWhenInsufficientFunds() {

        User user = new User();
        user.setFirst_name("Test");
        user.setLast_name("User");
        user.setEmail("fail@test.com");

        user = userRepository.save(user);

        Account acc = new Account();
        acc.setAccountNumber("ACC-FAIL");
        acc.setBalance(new BigDecimal("100"));
        acc.setUser(user);

        acc = accountRepository.save(acc);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            service.retrait("ACC-FAIL", new BigDecimal("500"));
        });

        assertEquals("Fonds insuffisants", ex.getMessage());

        Account updated = accountRepository.findById(acc.getId()).orElseThrow();

        assertMoney(new BigDecimal("100"), updated.getBalance());
    }

    // CONCURRENT DEPOSIT
    @Test
    void shouldHandleConcurrentDepositsCorrectly() throws Exception {

        User user = new User();
        user.setFirst_name("Concurrent");
        user.setLast_name("User");
        user.setEmail("deposit-" + System.nanoTime() + "@mail.com");
        user = userRepository.save(user);

        Account acc = new Account();
        acc.setAccountNumber("ACC-DEPOSIT-" + System.nanoTime());
        acc.setBalance(BigDecimal.ZERO);
        acc.setUser(user);

        acc = accountRepository.save(acc);

        final String accountNumber = acc.getAccountNumber();

        int operations = 100;
        BigDecimal amount = BigDecimal.TEN;

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(operations);

        for (int i = 0; i < operations; i++) {
            executor.submit(() -> {
                try {
                    service.depot(accountNumber, amount);
                } catch (Exception e) {
                    fail("Erreur deposit: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(15, TimeUnit.SECONDS);
        assertTrue(completed, "Timeout deposit");

        executor.shutdown();

        Account updated = accountRepository.findById(acc.getId()).orElseThrow();

        assertMoney(amount.multiply(BigDecimal.valueOf(operations)), updated.getBalance());
    }
}
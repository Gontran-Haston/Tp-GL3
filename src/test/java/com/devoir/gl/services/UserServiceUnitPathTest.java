package com.devoir.gl.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.devoir.gl.entities.Bank;
import com.devoir.gl.entities.BankAccount;
import com.devoir.gl.entities.BankAccount.AccountSubType;
import com.devoir.gl.entities.BankAccount.AccountType;
import com.devoir.gl.entities.User;
import com.devoir.gl.repositories.BankRepository;
import com.devoir.gl.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceUnitPathTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BankRepository bankRepository;

    @Mock
    private BankService bankService;

    @InjectMocks
    private UserService userService;

    @Test
    void pathCreateUserNullInputThrowsException() {

        Exception ex = assertThrows(
                Exception.class,
                () -> userService.createUser(null)
        );

        assertEquals(
                "Erreur --- Nouvel utilisateur vide",
                ex.getMessage()
        );
    }

    @Test
    void pathCreateUserWithoutDefaultBankSavesOnlyUser() throws Exception {

        User input = new User();
        input.setFirst_name("Alice");
        input.setLast_name("Unit");
        input.setEmail("alice.unit@test.com");

        User saved = new User();
        saved.setId(1L);
        saved.setFirst_name("Alice");
        saved.setLast_name("Unit");
        saved.setEmail("alice.unit@test.com");

        when(userRepository.save(input)).thenReturn(saved);
        when(bankRepository.findAll()).thenReturn(List.of());

        CompletableFuture<User> future = userService.createUser(input);

        User result = future.get();

        assertSame(saved, result);

        // Aucun compte créé
        verify(bankService, never())
                .createClientAccount(anyLong(), anyLong(), any());
    }

    @Test
    void pathCreateUserWithDefaultBankCreatesClientBankAccount() throws Exception {

        User input = new User();
        input.setFirst_name("Bob");
        input.setLast_name("Unit");
        input.setEmail("bob.unit@test.com");

        User saved = new User();
        saved.setId(1L);
        saved.setFirst_name("Bob");
        saved.setLast_name("Unit");
        saved.setEmail("bob.unit@test.com");

        Bank bank = new Bank();
        bank.setId(10L);
        bank.setIbanPrefix("FR");
        bank.setName("Bank Unit");
        bank.setSwiftCode("BNKUTEST");
        bank.setCountry("France");

        BankAccount createdAccount = new BankAccount();
        createdAccount.setAccountType(AccountType.CLIENT);
        createdAccount.setAccountSubtype(AccountSubType.CHECKING);
        createdAccount.setBalance(BigDecimal.ZERO);

        when(userRepository.save(input)).thenReturn(saved);

        when(bankRepository.findAll())
                .thenReturn(List.of(bank));

        // Mock important sinon erreur de test path
        when(bankService.createClientAccount(
                eq(saved.getId()),
                eq(bank.getId()),
                eq(AccountSubType.CHECKING)
        )).thenReturn(createdAccount);

        User result = userService.createUser(input).get();

        assertSame(saved, result);

        // Vérifie que le service a bien été appelé
        verify(bankService).createClientAccount(
                saved.getId(),
                bank.getId(),
                AccountSubType.CHECKING
        );

        assertNotNull(createdAccount);
        assertEquals(AccountType.CLIENT, createdAccount.getAccountType());
        assertEquals(AccountSubType.CHECKING, createdAccount.getAccountSubtype());
        assertEquals(0, BigDecimal.ZERO.compareTo(createdAccount.getBalance()));
    }

    @Test
    void pathFindAllUsersReturnsRepositoryContent() {

        User u1 = new User();
        u1.setEmail("u1@test.com");

        User u2 = new User();
        u2.setEmail("u2@test.com");

        when(userRepository.findAll())
                .thenReturn(List.of(u1, u2));

        List<User> result = userService.findAllUsers();

        assertEquals(2, result.size());
        assertEquals("u1@test.com", result.get(0).getEmail());
    }

    @Test
    void pathRateLimiterFallbackReturnsExpectedMessage() {

        String result = userService.callBackApiUsing(
                new RuntimeException("boom")
        );

        assertEquals(
                "Limite de requêtes atteinte. Veuillez réessayer plus tard.",
                result
        );
    }
}
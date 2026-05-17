package com.devoir.gl.services;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.devoir.gl.entities.Bank;
import com.devoir.gl.entities.BankAccount.AccountSubType;
import com.devoir.gl.entities.User;
import com.devoir.gl.repositories.BankRepository;
import com.devoir.gl.repositories.UserRepository;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BankRepository bankRepository;

    @Autowired
    private BankService bankService;

    @Async("taskExecutor")
    @RateLimiter(name = "userApi", fallbackMethod = "callBackApiUsing")
    public CompletableFuture<User> createUser(User newUser) throws Exception {

        if (newUser == null) {
            throw new Exception("Erreur --- Nouvel utilisateur vide");
        }

        User savedUser = userRepository.save(newUser);

        // Création automatique d'un compte CHECKING
        List<Bank> banks = bankRepository.findAll();

        if (!banks.isEmpty()) {

            Bank defaultBank = banks.get(0);

            bankService.createClientAccount(
                    savedUser.getId(),
                    defaultBank.getId(),
                    AccountSubType.CHECKING
            );
        }

        return CompletableFuture.completedFuture(savedUser);
    }

    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    public CompletableFuture<User> callBackApiUsing(User newUser, Throwable t) {

        throw new RuntimeException(
                "Limite de requêtes atteinte. Veuillez réessayer plus tard.",
                t
        );
    }

    public String callBackApiUsing(Throwable t) {
        return "Limite de requêtes atteinte. Veuillez réessayer plus tard.";
    }
}
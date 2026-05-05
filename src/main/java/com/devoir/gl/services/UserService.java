package com.devoir.gl.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import com.devoir.gl.entities.Account;
import com.devoir.gl.entities.User;
import com.devoir.gl.repositories.AccountRepository;
import com.devoir.gl.repositories.UserRepository;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Async("taskExecutor")
    @RateLimiter(name = "userApi", fallbackMethod = "callBackApiUsing")
    public CompletableFuture<User> createUser(User newUser) throws Exception {

        if (newUser == null) {
            throw new Exception("Erreur --- Nouvel utilisateur vide");
        }

        User savedUser = userRepository.save(newUser);

        Account account = new Account();
        account.setAccountNumber(generateAccountNumber());
        account.setBalance(BigDecimal.ZERO);
        account.setUser(savedUser);

        accountRepository.save(account);

        return CompletableFuture.completedFuture(savedUser);
    }
    
    @Async("taskExecutor")
    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    public String callBackApiUsing(Throwable t) {
        return "Limite de requêtes atteinte. Veuillez réessayer plus tard.";
    }

    private String generateAccountNumber() {
        return "ACC-" + System.currentTimeMillis();
    }
}

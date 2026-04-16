package com.devoir.gl.services;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import com.devoir.gl.entities.User;
import com.devoir.gl.repositories.UserRepository;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

@Service
public class UserService {
	
	@Autowired
	private UserRepository userRepository;
	
	@Async("taskExecutor")
	@RateLimiter(name = "userApi", fallbackMethod = "callBackApiUsing")
	public CompletableFuture<User> createUser(@RequestParam User newUser) throws Exception {
		if(newUser == null) throw new Exception("Erreur --- Nouvel utilisateur vide");
		if(newUser.getBalance() < 0.0) throw new Exception("Erreur --- Compte negatif");
		
		return CompletableFuture.completedFuture(userRepository.save(newUser));
	}
	
	public List<User> findAllUsers(){
		return userRepository.findAll();
	}
	
	public String callBackApiUsing(Throwable t) {
		return "Limite de requêtes atteinte. Veuillez réessayer plus tard.";
	}
}

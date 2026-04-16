package com.devoir.gl.controllers;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devoir.gl.entities.User;
import com.devoir.gl.services.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Gestion des utilisateurs du systeme bancaire simplifie", description = "API pour creer et gerer les utilisateurs")
public class UserController {
	
	@Autowired
	private UserService userService;
	
	@Operation(summary = "Recupere tous les utilisateurs inscrits dans le systeme", description = "Retourne tous les details des utilisateurs presents en BD")
	@ApiResponse(responseCode = "200", description = "Utilisateurs trouvés si la liste est non vide ou vide")
	@GetMapping("/all")
	public ResponseEntity<List<User>> getAllUsers() {
		return ResponseEntity.ok(userService.findAllUsers());
	}
	
	@PostMapping
	@Operation(summary = "Cree un utilisateur dans le systeme", description = "Enregistre un nouvel user en BD")
	@ApiResponse(responseCode = "200", description = "Ajoute un nouvel utilisateur si le format des donnees est respecte")
	@ApiResponse(responseCode = "400", description = "Impossible d'ajouter car le format de donnees n'est pas respecte")
	public CompletableFuture<ResponseEntity<User>> createUser(@RequestBody @Valid User newUser) throws Exception {
		return userService.createUser(newUser)
				.thenApply(saved -> ResponseEntity.ok(saved));
	}
	
}

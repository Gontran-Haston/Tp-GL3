package com.devoir.gl.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devoir.gl.entities.Bank;
import com.devoir.gl.entities.BankAccount;
import com.devoir.gl.services.BankService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/banks")
// @CrossOrigin(origins = "*") // Permet les requêtes depuis n'importe quelle origine (à ajuster en production)
@Tag(name = "Gestion des banques", description = "API pour gérer les institutions bancaires et leurs comptes")
public class BankController {

	@Autowired
	private BankService bankService;

	@PostMapping
	@Operation(summary = "Créer une nouvelle banque", description = "Enregistre une nouvelle institution bancaire dans le système")
	@ApiResponse(responseCode = "200", description = "Banque créée avec succès")
	@ApiResponse(responseCode = "400", description = "Données invalides ou banque existante")
	public ResponseEntity<Bank> createBank(@RequestBody @Valid Bank bank) {
		return ResponseEntity.ok(bankService.createBank(bank));
	}

	@GetMapping
	@Operation(summary = "Lister toutes les banques", description = "Récupère la liste de toutes les institutions bancaires")
	@ApiResponse(responseCode = "200", description = "Liste des banques")
	public ResponseEntity<List<Bank>> getAllBanks() {
		return ResponseEntity.ok(bankService.getAllBanks());
	}

	@GetMapping("/{swiftCode}")
	@Operation(summary = "Récupérer une banque par SWIFT", description = "Récupère les détails d'une banque à partir de son code SWIFT")
	@ApiResponse(responseCode = "200", description = "Banque trouvée")
	@ApiResponse(responseCode = "404", description = "Banque non trouvée")
	public ResponseEntity<Bank> getBankBySwiftCode(@PathVariable String swiftCode) {
		return ResponseEntity.ok(
				bankService.getBankBySwiftCode(swiftCode)
						.orElseThrow(() -> new RuntimeException("Banque non trouvée")));
	}

	@PostMapping("/{bankId}/client-account")
	@Operation(summary = "Créer un compte client", description = "Ouvre un compte client pour un utilisateur dans une banque")
	@ApiResponse(responseCode = "200", description = "Compte créé avec succès")
	@ApiResponse(responseCode = "400", description = "Utilisateur ou banque non trouvé(e)")
	public ResponseEntity<BankAccount> createClientAccount(
			@PathVariable Long bankId,
			@RequestBody Long userId) {
		return ResponseEntity.ok(bankService.createClientAccount(userId, bankId));
	}

	@PostMapping("/{bankOwnerId}/interbank-account/{linkedBankId}")
	@Operation(summary = "Créer un compte inter-banque", description = "Ouvre un compte de correspondance bancaire")
	@ApiResponse(responseCode = "200", description = "Compte inter-banque créé avec succès")
	@ApiResponse(responseCode = "400", description = "Banques non trouvées")
	public ResponseEntity<BankAccount> createInterbankAccount(
			@PathVariable Long bankOwnerId,
			@PathVariable Long linkedBankId) {
		return ResponseEntity.ok(bankService.createInterbankAccount(bankOwnerId, linkedBankId));
	}

	@GetMapping("/{bankId}/client-accounts")
	@Operation(summary = "Lister les comptes clients", description = "Récupère tous les comptes clients d'une banque")
	@ApiResponse(responseCode = "200", description = "Liste des comptes clients")
	public ResponseEntity<List<BankAccount>> getClientAccounts(@PathVariable Long bankId) {
		return ResponseEntity.ok(bankService.getClientAccounts(bankId));
	}

	@GetMapping("/{bankId}/interbank-accounts")
	@Operation(summary = "Lister les comptes inter-banques", description = "Récupère tous les comptes de correspondance d'une banque")
	@ApiResponse(responseCode = "200", description = "Liste des comptes inter-banques")
	public ResponseEntity<List<BankAccount>> getInterbankAccounts(@PathVariable Long bankId) {
		return ResponseEntity.ok(bankService.getInterbankAccounts(bankId));
	}
}

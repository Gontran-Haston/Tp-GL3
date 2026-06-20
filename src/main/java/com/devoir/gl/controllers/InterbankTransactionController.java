package com.devoir.gl.controllers;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devoir.gl.services.InterbankTransactionService;
import com.devoir.gl.utils.TransactionResDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Contrôleur pour les transactions inter-banques et intra-banques
 */
@RestController
@RequestMapping("/api/interbank-transactions")
// @CrossOrigin(origins = "*") // Permet les requêtes depuis n'importe quelle origine (à ajuster en production)
@Tag(name = "Transactions inter-banques", description = "API pour gérer les virements entre comptes (intra et inter-banques)")
public class InterbankTransactionController {

	@Autowired
	private InterbankTransactionService transactionService;

	@PostMapping("/transfer")
	@Operation(summary = "Virement inter-banques", description = "Effectue un virement entre deux comptes IBAN (peut être inter-banques ou intra-banque)")
	@ApiResponse(responseCode = "200", description = "Virement effectué avec succès")
	@ApiResponse(responseCode = "400", description = "Montant invalide ou compte non trouvé")
	public ResponseEntity<Void> transferInterbank(@RequestBody TransferRequest request) {
		transactionService.transferInterbank(
				request.getFromIban(),
				request.getToIban(),
				request.getAmount());
		return ResponseEntity.ok().build();
	}

	@PostMapping("/transfer-by-account")
	@Operation(summary = "Virement par numéro de compte", description = "Effectue un virement entre deux numéros de compte (intra-banque)")
	@ApiResponse(responseCode = "200", description = "Virement effectué avec succès")
	@ApiResponse(responseCode = "400", description = "Montant invalide ou compte non trouvé")
	public ResponseEntity<Void> transferIntrabank(@RequestBody TransferRequest request) {
		transactionService.transferIntrabank(
				request.getFromAccountNumber(),
				request.getToAccountNumber(),
				request.getAmount());
		return ResponseEntity.ok().build();
	}

	@PostMapping("/deposit")
	@Operation(summary = "Dépôt sur compte", description = "Effectue un dépôt sur un compte IBAN")
	@ApiResponse(responseCode = "200", description = "Dépôt effectué")
	@ApiResponse(responseCode = "400", description = "Compte non trouvé")
	public ResponseEntity<Void> deposit(@RequestBody DepositWithdrawRequest request) {
		transactionService.deposit(request.getIban(), request.getAmount());
		return ResponseEntity.ok().build();
	}

	@PostMapping("/withdraw")
	@Operation(summary = "Retrait depuis compte", description = "Effectue un retrait depuis un compte IBAN")
	@ApiResponse(responseCode = "200", description = "Retrait effectué")
	@ApiResponse(responseCode = "400", description = "Fonds insuffisants ou compte non trouvé")
	public ResponseEntity<Void> withdraw(@RequestBody DepositWithdrawRequest request) {
		transactionService.withdraw(request.getIban(), request.getAmount());
		return ResponseEntity.ok().build();
	}

	@GetMapping("/history/{iban}")
	@Operation(summary = "Historique des transactions", description = "Récupère l'historique des transactions d'un compte IBAN")
	@ApiResponse(responseCode = "200", description = "Historique trouvé")
	@ApiResponse(responseCode = "404", description = "Compte non trouvé")
	public ResponseEntity<List<TransactionResDTO>> getHistory(@PathVariable String iban) {
		return ResponseEntity.ok(transactionService.getHistory(iban));
	}

	public static class TransferRequest {
		private String fromIban;
		private String toIban;
		private String fromAccountNumber;
		private String toAccountNumber;
		private BigDecimal amount;

		public String getFromIban() {
			return fromIban;
		}

		public void setFromIban(String fromIban) {
			this.fromIban = fromIban;
		}

		public String getToIban() {
			return toIban;
		}

		public void setToIban(String toIban) {
			this.toIban = toIban;
		}

		public String getFromAccountNumber() {
			return fromAccountNumber;
		}

		public void setFromAccountNumber(String fromAccountNumber) {
			this.fromAccountNumber = fromAccountNumber;
		}

		public String getToAccountNumber() {
			return toAccountNumber;
		}

		public void setToAccountNumber(String toAccountNumber) {
			this.toAccountNumber = toAccountNumber;
		}

		public BigDecimal getAmount() {
			return amount;
		}

		public void setAmount(BigDecimal amount) {
			this.amount = amount;
		}
	}

	public static class DepositWithdrawRequest {
		private String iban;
		private BigDecimal amount;

		public String getIban() {
			return iban;
		}

		public void setIban(String iban) {
			this.iban = iban;
		}

		public BigDecimal getAmount() {
			return amount;
		}

		public void setAmount(BigDecimal amount) {
			this.amount = amount;
		}
	}
}

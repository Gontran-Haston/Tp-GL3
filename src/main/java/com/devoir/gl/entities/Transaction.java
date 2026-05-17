package com.devoir.gl.entities;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.devoir.gl.utils.TransactionType;

@Entity @AllArgsConstructor @NoArgsConstructor
@Table(name="transactions")
@Schema(description = "Représente une transaction bancaire (dépôt, retrait ou virement)")
public class Transaction {
	
	@Id @Getter
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@Getter @Setter
	@PositiveOrZero
	private BigDecimal amount;
	
	@Getter @Setter
	private LocalDateTime timestamp;
	
    @Enumerated(EnumType.STRING) @Getter @Setter
    private TransactionType type; // RETRAIT, DEPOT ou VIREMENT
	
	// Nouveau: Compte source pour virements inter-banques
	@ManyToOne
	@Getter @Setter
	@Schema(description = "Compte source (pour virements)")
	private BankAccount senderAccount;
	
	// Nouveau: Compte destinataire pour virements inter-banques
	@ManyToOne
	@Getter @Setter
	@Schema(description = "Compte destinataire (pour virements)")
	private BankAccount receiverAccount;
	
	@Getter @Setter
	private String description;

	@Getter @Setter
	private String reference;
	
	@Getter @Setter
	@Schema(description = "Statut du virement inter-banque: PENDING, COMPLETED, FAILED")
	private String transferStatus; // Pour les virements inter-banques
}

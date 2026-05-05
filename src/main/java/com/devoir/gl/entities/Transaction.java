package com.devoir.gl.entities;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.devoir.gl.utils.TransactionType;

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

@Entity @AllArgsConstructor @NoArgsConstructor
@Table(name="transactions")
@Schema(description = "Represente l'historique des transactions du compte d'un utilisateur")
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
    private TransactionType type; // RETRAIT OU DEPOT
	
	@ManyToOne @Getter @Setter
	private Account account;
	
	@Getter @Setter
	private String description;

	@Getter @Setter
	private String reference;
}

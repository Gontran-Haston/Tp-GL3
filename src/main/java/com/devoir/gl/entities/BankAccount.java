package com.devoir.gl.entities;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Représente un compte bancaire dans le système.
 * Peut être soit un compte client (lié à un User) soit un compte inter-banque
 * (lié à une autre banque pour les correspondances bancaires)
 */
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "bank_accounts")
@Data
@Schema(description = "Représente un compte bancaire (client ou inter-banque)")
public class BankAccount {

	public enum AccountType {
		CLIENT, // Compte d'un client/particulier
		INTERBANK // Compte de correspondance (autre banque)
	}
	
	public enum AccountSubType {
		SAVINGS, // Compte d'épargne
		CHECKING // Compte courant
	}

	@Id
	@Getter
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(unique = true, nullable = false)
	@NotNull
	@Schema(description = "Numéro de compte unique IBAN", example = "FR76 1234 5678 9012 3456 7890 123")
	@Getter
	@Setter
	private String iban;

	@Column(unique = true, nullable = false)
	@NotNull
	@Schema(description = "Numéro de compte simplifié", example = "ACC-1716893456789")
	@Getter
	@Setter
	private String accountNumber;

	@Getter
	@Setter
	@NotNull
	@Enumerated(EnumType.STRING)
	@Schema(description = "Type de compte: CLIENT pour particulier, INTERBANK pour autre banque")
	private AccountType accountType;

	@Getter
	@Setter
	@Enumerated(EnumType.STRING)
	@Column(columnDefinition = "VARCHAR(20) DEFAULT 'CHECKING'")
	@Schema(description = "Sous-type de compte pour les comptes clients: SAVINGS ou CHECKING (CHECKING par défaut)")
	private AccountSubType accountSubtype;

	@Getter
	@Setter
	private BigDecimal balance;

	@ManyToOne
	@NotNull
	@Schema(description = "Banque propriétaire de ce compte")
	@Getter
	@Setter
	private Bank bank;

	// Optionnel: Pour les comptes CLIENT, lien vers l'utilisateur
	@ManyToOne
	@Schema(description = "Utilisateur/Client propriétaire du compte (null si compte inter-banque)")
	@Getter
	@Setter
	private User user;

	// Optionnel: Pour les comptes INTERBANK, lien vers la banque liée
	@ManyToOne
	@Schema(description = "Banque correspondante (si compte inter-banque)")
	@Getter
	@Setter
	private Bank linkedBank;

	@Version
	@Getter
	@Setter
	private Long version;

	@OneToMany(mappedBy = "senderAccount", cascade = CascadeType.ALL, orphanRemoval = true)
	@Schema(description = "Transactions envoyées depuis ce compte")
	private List<Transaction> sentTransactions;

	@OneToMany(mappedBy = "receiverAccount", cascade = CascadeType.ALL, orphanRemoval = true)
	@Schema(description = "Transactions reçues sur ce compte")
	private List<Transaction> receivedTransactions;
}

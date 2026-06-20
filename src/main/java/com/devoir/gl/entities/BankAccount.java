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
import lombok.NoArgsConstructor;

/**
 * Représente un compte bancaire dans le système.
 * Peut être soit un compte client (lié à un User)
 * soit un compte inter-banque (lié à une autre banque).
 */
@Entity
@Table(name = "bank_accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Représente un compte bancaire (client ou inter-banque)")
public class BankAccount {

    public enum AccountType {
        CLIENT,
        INTERBANK
    }

    public enum AccountSubType {
        SAVINGS,
        CHECKING
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(unique = true, nullable = false)
    @Schema(
        description = "Numéro de compte unique IBAN",
        example = "FR76 1234 5678 9012 3456 7890 123"
    )
    private String iban;

    @NotNull
    @Column(unique = true, nullable = false)
    @Schema(
        description = "Numéro de compte simplifié",
        example = "ACC-1716893456789"
    )
    private String accountNumber;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Schema(
        description = "Type de compte : CLIENT pour particulier, INTERBANK pour autre banque"
    )
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true, length = 20)
    @Schema(
        description = "Sous-type de compte pour les comptes clients : SAVINGS ou CHECKING et NULL pour INTERBANK"
    )
    private AccountSubType accountSubtype = AccountSubType.CHECKING;

    @Column(nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;

    @ManyToOne
    @NotNull
    @Schema(description = "Banque propriétaire de ce compte")
    private Bank bank;

    @ManyToOne
    @Schema(
        description = "Utilisateur/Client propriétaire du compte (null si compte inter-banque)"
    )
    private User user;

    @ManyToOne
    @Schema(
        description = "Banque correspondante (si compte inter-banque)"
    )
    private Bank linkedBank;

    @Version
    private Long version;

    @OneToMany(
        mappedBy = "senderAccount",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @Schema(description = "Transactions envoyées depuis ce compte")
    private List<Transaction> sentTransactions;

    @OneToMany(
        mappedBy = "receiverAccount",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @Schema(description = "Transactions reçues sur ce compte")
    private List<Transaction> receivedTransactions;
}

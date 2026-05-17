package com.devoir.gl.entities;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @AllArgsConstructor @NoArgsConstructor
@Table(name="users") @Data
@Schema(description = "Représente un utilisateur/client du système bancaire")
public class User {
	
	@Id @Getter
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@Schema(description = "Nom d'utilisateur", example = "Zidane")
	@Getter @Setter @NotBlank
	private String first_name;
	
	@Schema(description = "Prénom d'utilisateur", example = "Paul")
	@Getter @Setter @NotBlank
	private String last_name;
	
	@Getter @Setter @Email @Column(unique = true) @NotNull
	@Schema(description = "Mail de l'utilisateur", example ="zzpaul@gmail.ducobu")
	private String email;
	
	// Nouveau: Comptes bancaires dans les différentes banques
	@OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
	@Schema(description = "Comptes bancaires du client dans les différentes banques")
	private List<BankAccount> bankAccounts;
	
}

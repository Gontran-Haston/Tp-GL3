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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "banks")
@Data
@Schema(description = "Représente une institution bancaire")
public class Bank {

	@Id
	@Getter
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Getter
	@Setter
	@NotBlank
	@Column(unique = true)
	@Schema(description = "Nom de la banque", example = "Banque de France")
	private String name;

	@Getter
	@Setter
	@NotBlank
	@Column(unique = true, length = 20)
	@Schema(description = "Code SWIFT identifiant unique de la banque", example = "BNFRFRPP")
	private String swiftCode;

	@Getter
	@Setter
	@NotBlank
	@Schema(description = "Pays de la banque", example = "France")
	private String country;

	@Getter
	@Setter
	@NotBlank
	@Schema(description = "Préfixe IBAN de la banque", example = "FR")
	private String ibanPrefix;

	@OneToMany(mappedBy = "bank", cascade = CascadeType.ALL, orphanRemoval = true)
	@Getter
	@Setter
	private List<BankAccount> accounts;

	@OneToMany(mappedBy = "linkedBank", cascade = CascadeType.ALL, orphanRemoval = true)
	@Getter
	@Setter
	private List<BankAccount> linkedAccounts; // Comptes inter-banques de cette banque chez d'autres banques
}

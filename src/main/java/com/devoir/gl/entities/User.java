package com.devoir.gl.entities;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @AllArgsConstructor @NoArgsConstructor
@Table(name="users")
@Schema(description = "Represente un utilisateur")
public class User {
	
	@Id @Getter
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@Schema(description = "Nom d'utilisateur", example = "Zidane")
	@Getter @Setter @NotBlank
	private String name;
	
	@Getter @Setter @Email
	@Schema(description = "Mail de l'utilisateur", example ="zzpaul@gmail.ducobu")
	private String email;
	
	@Getter @Setter @NotNull
	@Schema(description = "Solde de l'utilisateur", example= "3.00 FCFA")
	private Double balance;
	
}

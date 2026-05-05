package com.devoir.gl.utils;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

public class TransactionDTO {
	
	@Getter @Setter
	private String fromAccountNumber;
	
	@Getter @Setter
	private String toAccountNumber;
	
	@Getter @Setter
	@Positive
	@NotNull
	private BigDecimal amount;

}
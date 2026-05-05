package com.devoir.gl.utils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

public class TransactionResDTO {
	
	@Getter @Setter
	private BigDecimal amount;
    
	@Getter @Setter
	private String type;
    
	@Getter @Setter
	private LocalDateTime timestamp;
    
	@Getter @Setter
	private String description;
    
	@Getter @Setter
	private String reference;
}

package com.devoir.gl.utils;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

public class DepositAndRetraitReq {
	
	@Getter @Setter
	private String accountNumber;
	
	@Getter @Setter
    private BigDecimal amount;
}

package com.devoir.gl.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devoir.gl.services.TransactionService;
import com.devoir.gl.utils.DepositAndRetraitReq;
import com.devoir.gl.utils.TransactionDTO;
import com.devoir.gl.utils.TransactionResDTO;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private TransactionService transactionService;

//    @PostMapping
//    public ResponseEntity<Void> transfer(@RequestBody Transaction req) {
//    	transactionService.transfer(req.from(), req.to(), req.amount());
//        return ResponseEntity.ok().build();
//    }
    @PostMapping
    public ResponseEntity<Void> transfer(@RequestBody TransactionDTO req) {
        transactionService.transfer(
            req.getFromAccountNumber(),
            req.getToAccountNumber(),
            req.getAmount()
        );
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/{accountNumber}")
    public ResponseEntity<List<TransactionResDTO>> history(@PathVariable String accountNumber) {
        return ResponseEntity.ok(
            transactionService.getHistory(accountNumber)
        );
    }
    
    @PostMapping("/deposit")
    public ResponseEntity<Void> deposit(@RequestBody DepositAndRetraitReq req) {
        transactionService.depot(
                req.getAccountNumber(),
                req.getAmount()
        );
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/retrait")
    public ResponseEntity<Void> retrait(@RequestBody DepositAndRetraitReq req) {
        transactionService.retrait(
                req.getAccountNumber(),
                req.getAmount()
        );
        return ResponseEntity.ok().build();
    }
}


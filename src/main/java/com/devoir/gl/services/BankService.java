package com.devoir.gl.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.devoir.gl.entities.Bank;
import com.devoir.gl.entities.BankAccount;
import com.devoir.gl.entities.BankAccount.AccountSubType;
import com.devoir.gl.entities.BankAccount.AccountType;
import com.devoir.gl.entities.User;
import com.devoir.gl.repositories.BankAccountRepository;
import com.devoir.gl.repositories.BankRepository;
import com.devoir.gl.repositories.UserRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BankService {

    @Autowired
    private BankRepository bankRepository;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Crée une nouvelle banque
     */
    @Transactional
    public Bank createBank(Bank bank) {

        if (bank == null) {
            throw new IllegalArgumentException("La banque ne peut pas être nulle");
        }

        if (bankRepository.findBySwiftCode(bank.getSwiftCode()).isPresent()) {
            throw new RuntimeException("Une banque avec ce SWIFT existe déjà");
        }

        return bankRepository.save(bank);
    }

    /**
     * Crée un compte client
     */
    @Transactional
    public BankAccount createClientAccount(Long userId,
                                           Long bankId,
                                           AccountSubType subtype) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        Bank bank = bankRepository.findById(bankId)
                .orElseThrow(() -> new RuntimeException("Banque introuvable"));

        if (subtype == null) {
            subtype = AccountSubType.CHECKING;
        }

        BankAccount account = new BankAccount();

        account.setAccountNumber(generateAccountNumber(bank.getSwiftCode()));
        account.setIban(generateIBAN(bank.getIbanPrefix()));

        account.setBalance(BigDecimal.ZERO);

        account.setAccountType(AccountType.CLIENT);
        account.setAccountSubtype(subtype);

        account.setUser(user);
        account.setBank(bank);

        return bankAccountRepository.save(account);
    }

    /**
     * Compatibilité ancien code
     */
    @Transactional
    public BankAccount createClientAccount(Long userId, Long bankId) {
        return createClientAccount(
                userId,
                bankId,
                AccountSubType.CHECKING
        );
    }

    /**
     * Crée un compte interbancaire
     */
    @Transactional
    public BankAccount createInterbankAccount(Long bankOwnerId,
                                              Long linkedBankId) {

        Bank bankOwner = bankRepository.findById(bankOwnerId)
                .orElseThrow(() -> new RuntimeException("Banque propriétaire introuvable"));

        Bank linkedBank = bankRepository.findById(linkedBankId)
                .orElseThrow(() -> new RuntimeException("Banque liée introuvable"));

        if (bankOwnerId.equals(linkedBankId)) {
            throw new RuntimeException(
                    "Une banque ne peut pas avoir un compte chez elle-même"
            );
        }

        BankAccount account = new BankAccount();

        account.setAccountNumber(generateAccountNumber(linkedBank.getSwiftCode()));

        account.setIban(generateIBAN(linkedBank.getIbanPrefix()));

        account.setBalance(BigDecimal.ZERO);

        account.setAccountType(AccountType.INTERBANK);

        account.setAccountSubtype(null);

        account.setBank(linkedBank);

        account.setLinkedBank(bankOwner);

        return bankAccountRepository.save(account);
    }

    /**
     * Tous les comptes client d'une banque
     */
    public List<BankAccount> getClientAccounts(Long bankId) {

        return bankAccountRepository.findByBankId(bankId)
                .stream()
                .filter(acc ->
                        acc.getAccountType() == AccountType.CLIENT
                )
                .toList();
    }

    /**
     * Comptes CHECKING
     */
    public List<BankAccount> getCheckingAccounts(Long bankId) {

        return bankAccountRepository.findByBankId(bankId)
                .stream()
                .filter(acc ->
                        acc.getAccountSubtype() == AccountSubType.CHECKING
                )
                .toList();
    }

    /**
     * Comptes SAVINGS
     */
    public List<BankAccount> getSavingsAccounts(Long bankId) {

        return bankAccountRepository.findByBankId(bankId)
                .stream()
                .filter(acc ->
                        acc.getAccountSubtype() == AccountSubType.SAVINGS
                )
                .toList();
    }

    /**
     * Comptes interbancaires
     */
    public List<BankAccount> getInterbankAccounts(Long bankId) {

        return bankAccountRepository.findByBankId(bankId)
                .stream()
                .filter(acc ->
                        acc.getAccountType() == AccountType.INTERBANK
                )
                .toList();
    }

    /**
     * Comptes utilisateur dans une banque
     */
    public List<BankAccount> getUserAccounts(Long userId, Long bankId) {

        return bankAccountRepository.findByUserId(userId)
                .stream()
                .filter(acc ->
                        acc.getBank().getId().equals(bankId)
                )
                .toList();
    }

    /**
     * Tous les comptes utilisateur
     */
    public List<BankAccount> getAllUserAccounts(Long userId) {
        return bankAccountRepository.findByUserId(userId);
    }

    public Optional<Bank> getBankBySwiftCode(String swiftCode) {
        return bankRepository.findBySwiftCode(swiftCode);
    }

    public Optional<Bank> getBankByName(String name) {
        return bankRepository.findByName(name);
    }

    public List<Bank> getAllBanks() {
        return bankRepository.findAll();
    }

    private String generateAccountNumber(String swiftCode) {
        return "ACC-" + swiftCode + "-" + System.currentTimeMillis();
    }

    private String generateIBAN(String ibanPrefix) {
        return ibanPrefix + System.nanoTime();
    }
}
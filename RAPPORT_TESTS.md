# Rapport de Tests — Système Bancaire Spring Boot

**Projet :** `com.inf352 / devoir`  
**Date :** 11 juin 2026  
**Auteur :** sator680  
**Framework :** Spring Boot 4.0.5 · JUnit 5 · JaCoCo · Testcontainers (PostgreSQL)

---

## Table des matières

1. [Présentation du système](#1-présentation-du-système)
2. [TransactionService](#2-transactionservice)
3. [BankService](#3-bankservice)
4. [InterbankTransactionService](#4-interbanktransactionservice)
5. [UserService](#5-userservice)
6. [Tableau de synthèse global](#6-tableau-de-synthèse-global)
7. [Observations et recommandations](#7-observations-et-recommandations)

---

## 1. Présentation du système

### 1.1 Architecture

L'application implémente un système bancaire avec 4 services métier :

| Service | Responsabilité | Tests associés |
|---------|---------------|----------------|
| `TransactionService` | Virements intra-banque, dépôts, retraits par numéro de compte | `TransactionServiceTest` |
| `BankService` | Création de banques et comptes (CLIENT / INTERBANK) | `BankServiceTest`, `BankAccountSubtypeTest`, `BankAccountSubtypePathTest` |
| `InterbankTransactionService` | Virements inter/intra-banques par IBAN | `InterbankTransactionServicePathTest`, `BankServiceTest` |
| `UserService` | Création d'utilisateurs (async + rate limiter) avec compte auto | `UserServicePathTest`, `UserServiceUnitPathTest` |

### 1.2 Entités principales

```
Bank ──────< BankAccount >────── User
               │
               │ (senderAccount / receiverAccount)
               ▼
          Transaction
```

- **Bank** : code SWIFT unique, préfixe IBAN, pays
- **BankAccount** : `AccountType` ∈ {CLIENT, INTERBANK}, `AccountSubType` ∈ {CHECKING, SAVINGS}, solde, IBAN, numéro de compte
- **Transaction** : montant, type (DEPOSIT/WITHDRAW), timestamp, référence, statut
- **User** : prénom, nom, email, liste de comptes

### 1.3 Notation des graphes de flot de contrôle

```
┌─────────────────┐   = bloc séquentiel (nœud de traitement)
└─────────────────┘

     ◇ Condition ◇    = nœud de décision (branchement)

    ══ T (throw) ══    = sortie par exception
```

---

## 2. TransactionService

**Fichier :** `src/main/java/.../services/TransactionService.java`

### 2.1 Méthodes analysées

| # | Méthode | Rôle |
|---|---------|------|
| 1 | `transfer(fromAcc, toAcc, amount)` | Virement entre deux comptes |
| 2 | `depot(accountNumber, amount)` | Dépôt sur un compte |
| 3 | `retrait(accountNumber, amount)` | Retrait depuis un compte |
| 4 | `getHistory(accountNumber)` | Historique des transactions |
| 5 | `createTransaction(...)` | Fabrique interne de transaction |

---

### 2.2 `transfer(String fromAcc, String toAcc, BigDecimal amount)`

#### Graphe de Flot de Contrôle

```
                       ┌──────────────────────────────────────┐
                       │  ENTRÉE                              │
                       │  @Retry(dbRetry) @Transactional      │
                       └──────────────────────────────────────┘
                                         │
                                         ▼
                       ┌──────────────────────────────────────┐
                  N1   │ from = findByAccountNumberForUpdate  │
                       │        (fromAcc)                     │
                       └──────────────────────────────────────┘
                                         │
                             ◇ from présent? ◇
                            /                  \
                          Non                  Oui
                           │                   │
               ════════════════         ┌──────────────────────────────────────┐
               T1: RuntimeException N2  │ to = findByAccountNumberForUpdate   │
               "Compte source           │      (toAcc)                        │
                introuvable"            └──────────────────────────────────────┘
                                                          │
                                            ◇ to présent? ◇
                                           /                \
                                         Non               Oui
                                          │                 │
                              ═══════════════     ◇ from.accountNumber == to.accountNumber? ◇
                              T2: "Compte         /                           \
                               destination       Oui                         Non
                               introuvable"       │                           │
                                       ══════════════    ◇ amount <= 0? ◇
                                       T3: "Auto-       /               \
                                        transfert      Oui              Non
                                        interdit"       │                │
                                              ══════════     ◇ balance < amount? ◇
                                              T4: "Montant  /                 \
                                               invalide"   Oui               Non
                                                        ══════════    ┌──────────────────────────────────┐
                                                        T5: "Fonds N6 │ from.balance -= amount          │
                                                         insuffisants"│ to.balance   += amount          │
                                                                      │ Création debit + credit Tx      │
                                                                      │ save(from), save(to)            │
                                                                      │ save(debit), save(credit)       │
                                                                      └──────────────────────────────────┘
                                                                                      │
                                                                                   SORTIE
```

... (file continues)

| Chemin | Séquence de nœuds | Condition de déclenchement |
|--------|-------------------|---------------------------|
| **P1** | N1 → T1 | Compte source introuvable |
| **P2** | N1 → N2 → T2 | Compte destination introuvable |
| **P3** | N1 → N2 → D3(vrai) → T3 | Auto-transfert (`fromAcc == toAcc`) |
| **P4** | N1 → N2 → D3(faux) → D4(vrai) → T4 | Montant ≤ 0 |
| **P5** | N1 → N2 → D3(faux) → D4(faux) → D5(vrai) → T5 | Solde insuffisant |
| **P6** | N1 → N2 → D3(faux) → D4(faux) → D5(faux) → N6 | Succès |

**Nombre total de chemins :** 6

#### Statement Coverage

| Statement (S#) | Code | Couvert par test |
|---------------|------|-----------------|
| S1 | `from = findByAccountNumberForUpdate(fromAcc)` | `shouldHandleHighConcurrencyTransfers` |
| S2 | `throw RuntimeException("Compte source introuvable")` | Non testé |
| S3 | `to = findByAccountNumberForUpdate(toAcc)` | |
| S4 | `throw RuntimeException("Compte destination introuvable")` | Non testé |
| S5 | `if (from.getAccountNumber().equals(to.getAccountNumber()))` | (évalué à faux) |
| S6 | `throw RuntimeException("Auto-transfert interdit")` | Non testé |
| S7 | `if (amount.compareTo(ZERO) <= 0)` | (évalué à faux) |
| S8 | `throw RuntimeException("Montant invalide")` | Non testé |
| S9 | `if (from.getBalance().compareTo(amount) < 0)` | (évalué à faux) |
| S10 | `throw RuntimeException("Fonds insuffisants")` | Non testé dans transfer |
| S11 | `from.setBalance(from.getBalance().subtract(amount))` | |
| S12 | `to.setBalance(to.getBalance().add(amount))` | |
| S13 | `String ref = UUID.randomUUID().toString()` | |
| S14 | `createTransaction(from, to, ..., WITHDRAW, ...)` | |
| S15 | `createTransaction(from, to, ..., DEPOSIT, ...)` | |
| S16 | `bankAccountRepository.save(from)` | |
| S17 | `bankAccountRepository.save(to)` | |
| S18 | `transactionRepository.save(debit)` | |
| S19 | `transactionRepository.save(credit)` | |

**Statement Coverage = 14/19 ≈ 74%**

#### Branch Coverage

| Branche | Condition | Vrai couvert | Faux couvert |
|---------|-----------|:------------:|:------------:|
| B1 | `from` introuvable |  | |
| B2 | `to` introuvable |  | |
| B3 | `fromAcc == toAcc` |  | |
| B4 | `amount <= 0` |  | |
| B5 | `balance < amount` |  | |

**Branches couvertes : 5 / 10**  
**Branch Coverage = 50%**

#### Path Coverage

| Chemin | Test qui le couvre |
|--------|-------------------|
| P1 | Non couvert |
| P2 | Non couvert |
| P3 | Non couvert |
| P4 | Non couvert |
| P5 | Non couvert (la méthode `shouldFailWithdrawWhenInsufficientFunds` cible `retrait`, pas `transfer`) |
| P6 | `shouldHandleHighConcurrencyTransfers` |

**Path Coverage = 1/6 ≈ 17%**

---

### 2.3 `depot(String accountNumber, BigDecimal amount)`

#### Graphe de Flot de Contrôle

```
         ┌─────────────────────────────────┐
         │ ENTRÉE  @Transactional          │
         └─────────────────────────────────┘
                          │
                          ▼
         ┌─────────────────────────────────┐
    N1   │ acc = findByAccountNumberForUpdate│
         │       (accountNumber)           │
         └─────────────────────────────────┘
                          │
              ◇ acc présent? ◇
             /                \
           Non                Oui
            │                  │
     ══════════════    ◇ amount <= 0? ◇
     T1: "Compte      /              \
      introuvable"  Oui             Non
                     │               │
              ══════════   ┌──────────────────────────┐
              T2: "Montant │ acc.balance += amount    │
               invalide"  │ save(acc)                │
                           │ save(createTransaction)  │
                           └──────────────────────────┘
                                         │
                                      SORTIE
```

#### Enumération des chemins

| Chemin | Séquence | Condition |
|--------|----------|-----------|
| **P1** | N1 → T1 | Compte introuvable |
| **P2** | N1 → D2(vrai) → T2 | Montant ≤ 0 |
| **P3** | N1 → D2(faux) → N3 | Succès |

**Nombre total de chemins : 3**

#### Statement Coverage

| Statement | Couvert |
|-----------|---------|
| `acc = findByAccountNumberForUpdate(accountNumber)` | |
| `throw "Compte introuvable"` | |
| `if (amount <= 0)` | |
| `throw "Montant invalide"` | |
| `acc.setBalance(acc.getBalance().add(amount))` | |
| `bankAccountRepository.save(acc)` | |
| `transactionRepository.save(createTransaction(...))` | |

**Statement Coverage = 5/7 ≈ 71%**

#### Branch Coverage

| Branche | Vrai couvert | Faux couvert |
|---------|:------------:|:------------:|
| `acc` introuvable |  | |
| `amount <= 0` |  | |

**Branch Coverage = 2/4 = 50%**

#### Path Coverage

| Chemin | Test |
|--------|------|
| P1 | Non couvert |
| P2 | Non couvert |
| P3 | `shouldDepositAndWithdrawMoneyCorrectly` |

**Path Coverage = 1/3 ≈ 33%**

---

### 2.4 `retrait(String accountNumber, BigDecimal amount)`

#### Graphe de Flot de Contrôle

```
         ┌─────────────────────────────────┐
         │ ENTRÉE  @Transactional          │
         └─────────────────────────────────┘
                          │
                          ▼
         ┌─────────────────────────────────┐
    N1   │ acc = findByAccountNumberForUpdate│
         │       (accountNumber)           │
         └─────────────────────────────────┘
                          │
              ◇ acc présent? ◇
             /                \
           Non                Oui
            │                  │
     ══════════════   ◇ amount <= 0? ◇
     T1: "Compte     /              \
      introuvable" Oui             Non
                    │               │
             ══════════   ◇ balance < amount? ◇
             T2: "Montant /                 \
              invalide" Oui               Non
                         │                 │
                 ════════════   ┌────────────────────────────┐
                 T3: "Fonds  N4 │ acc.balance -= amount      │
                  insuffisants" │ save(acc)                  │
                                │ save(createTransaction)    │
                                └────────────────────────────┘
                                              │
                                           SORTIE
```

#### Enumération des chemins

| Chemin | Séquence | Condition |
|--------|----------|-----------|
| **P1** | N1 → T1 | Compte introuvable |
| **P2** | N1 → D2(vrai) → T2 | Montant ≤ 0 |
| **P3** | N1 → D2(faux) → D3(vrai) → T3 | Fonds insuffisants |
| **P4** | N1 → D2(faux) → D3(faux) → N4 | Succès |

**Nombre total de chemins : 4**

#### Statement Coverage

| Statement | Couvert |
|-----------|---------|
| `acc = findByAccountNumberForUpdate(accountNumber)` | |
| `throw "Compte introuvable"` | |
| `if (amount <= 0)` | (évalué à faux) |
| `throw "Montant invalide"` | |
| `if (balance < amount)` | |
| `throw "Fonds insuffisants"` | |
| `acc.setBalance(acc.getBalance().subtract(amount))` | |
| `bankAccountRepository.save(acc)` | |
| `transactionRepository.save(createTransaction(...))` | |

**Statement Coverage = 7/9 ≈ 78%**

#### Branch Coverage

| Branche | Vrai couvert | Faux couvert |
|---------|:------------:|:------------:|
| `acc` introuvable |  | |
| `amount <= 0` |  |  |
| `balance < amount` |  |  |

**Branch Coverage = 4/6 ≈ 67%**

#### Path Coverage

| Chemin | Test |
|--------|------|
| P1 |  Non couvert |
| P2 |  Non couvert |
| P3 |  `shouldFailWithdrawWhenInsufficientFunds` |
| P4 |  `shouldDepositAndWithdrawMoneyCorrectly` |

**Path Coverage = 2/4 = 50%**

---

### 2.5 `getHistory(String accountNumber)`

#### Graphe de Flot de Contrôle

```
    ┌─────────────────────────────────────────────────────┐
    │ ENTRÉE                                              │
    └─────────────────────────────────────────────────────┘
                             │
                             ▼
    ┌─────────────────────────────────────────────────────┐
N1  │ Stream.concat(                                      │
    │   findBySenderAccount(accountNumber),               │
    │   findByReceiverAccount(accountNumber)              │
    │ )                                                   │
    └─────────────────────────────────────────────────────┘
                             │
                             ▼
    ┌─────────────────────────────────────────────────────┐
N2  │ .sorted(by timestamp descending)                   │
    └─────────────────────────────────────────────────────┘
                             │
                             ▼
    ┌─────────────────────────────────────────────────────┐
N3  │ .map(tx → TransactionResDTO{amount, type,          │
    │         timestamp, description, reference})         │
    └─────────────────────────────────────────────────────┘
                             │
                             ▼
    ┌─────────────────────────────────────────────────────┐
N4  │ return .toList()                                    │
    └─────────────────────────────────────────────────────┘
                             │
                          SORTIE
```

> Pas de branchement explicite. La méthode est linéaire (les opérations de stream ne constituent pas des branches au sens du flot de contrôle).

**Chemin unique : P1 = N1 → N2 → N3 → N4**

#### Couverture

| Métrique | Valeur |
|----------|--------|
| Statement Coverage |  **0%** — `getHistory` de `TransactionService` n'est pas testé dans `TransactionServiceTest` |
| Branch Coverage | N/A (pas de branche) |
| Path Coverage |  **0%** |

> Note : `InterbankTransactionService.getHistory()` (méthode différente) est, elle, testée.

---

### 2.6 `createTransaction(...)` — méthode privée

Méthode entièrement séquentielle, appelée depuis `transfer`, `depot`, et `retrait`.

**Statement Coverage : 100%** (appelée via les chemins couverts des méthodes publiques)

---

### Synthèse TransactionService

| Méthode | Statement | Branch | Path |
|---------|:---------:|:------:|:----:|
| `transfer` | 74% | 50% | 17% (1/6) |
| `depot` | 71% | 50% | 33% (1/3) |
| `retrait` | 78% | 67% | 50% (2/4) |
| `getHistory` | 0% | N/A | 0% (0/1) |
| `createTransaction` | 100% | N/A | 100% |
| **TOTAL** | **≈ 65%** | **≈ 55%** | **≈ 29%** |

---

## 3. BankService

**Fichier :** `src/main/java/.../services/BankService.java`

### 3.1 Méthodes analysées

| # | Méthode |
|---|---------|
| 1 | `createBank(Bank bank)` |
| 2 | `createClientAccount(userId, bankId, subtype)` |
| 3 | `createClientAccount(userId, bankId)` — surcharge |
| 4 | `createInterbankAccount(bankOwnerId, linkedBankId)` |
| 5 | `getClientAccounts(bankId)` |
| 6 | `getCheckingAccounts(bankId)` |
| 7 | `getSavingsAccounts(bankId)` |
| 8 | `getInterbankAccounts(bankId)` |
| 9 | `getUserAccounts(userId, bankId)` |
| 10 | `getAllUserAccounts(userId)` |

---

### 3.2 `createBank(Bank bank)`

#### Graphe de Flot de Contrôle

```
    ┌──────────────────────────────────┐
    │ ENTRÉE  @Transactional           │
    └──────────────────────────────────┘
                      │
                      ▼
         ◇ bank == null? ◇
        /                \
       Oui              Non
        │                │
 ══════════════  ┌────────────────────────────────────┐
 T1: IllegalArg N2│ bankRepository.findBySwiftCode(  │
  "banque nulle"  │   bank.getSwiftCode())           │
                  └────────────────────────────────────┘
                                   │
                  ◇ SWIFT déjà présent? ◇
                 /                     \
               Oui                    Non
                │                      │
      ════════════════     ┌─────────────────────────┐
      T2: RuntimeException │ return bankRepository  │
      "SWIFT existe déjà"  │       .save(bank)      │
                           └─────────────────────────┘
                                        │
                                     SORTIE
```

#### Enumération des chemins

| Chemin | Condition |
|--------|-----------|
| **P1** | `bank == null` → `IllegalArgumentException` |
| **P2** | `bank != null`, SWIFT existe → `RuntimeException` |
| **P3** | `bank != null`, SWIFT libre → succès |

**Nombre total de chemins : 3**

#### Statement Coverage

| Statement | Couvert |
|-----------|---------|
| `if (bank == null)` |  (évalué à faux dans tous les setUp) |
| `throw IllegalArgumentException` |  |
| `findBySwiftCode(bank.getSwiftCode()).isPresent()` |  |
| `throw RuntimeException("SWIFT exists")` |  |
| `return bankRepository.save(bank)` |  |

**Statement Coverage = 3/5 = 60%**

#### Branch Coverage

| Branche | Vrai | Faux |
|---------|:----:|:----:|
| `bank == null` |  |  |
| SWIFT existe déjà |  |  |

**Branch Coverage = 2/4 = 50%**

#### Path Coverage

| Chemin | Test |
|--------|------|
| P1 |  Non couvert |
| P2 |  Non couvert |
| P3 |  `BankServiceTest.setUp()` (indirectement), `testCreateBank` |

**Path Coverage = 1/3 ≈ 33%**

---

### 3.3 `createClientAccount(Long userId, Long bankId, AccountSubType subtype)`

#### Graphe de Flot de Contrôle

```
         ┌─────────────────────────────────────────┐
         │ ENTRÉE  @Transactional                  │
         └─────────────────────────────────────────┘
                              │
                              ▼
         ┌─────────────────────────────────────────┐
    N1   │ user = userRepository.findById(userId)  │
         └─────────────────────────────────────────┘
                              │
                  ◇ user présent? ◇
                 /                \
               Non               Oui
                │                 │
       ══════════════    ┌─────────────────────────────────────────┐
       T1: "Utilisateur N2│ bank = bankRepository.findById(bankId) │
        introuvable"     └─────────────────────────────────────────┘
                                           │
                               ◇ bank présent? ◇
                              /                \
                            Non               Oui
                             │                 │
                   ══════════════    ◇ subtype == null? ◇
                   T2: "Banque      /                  \
                    introuvable"  Oui                  Non
                                   │                    │
                            ┌──────────────┐   (subtype conservé)
                            │ subtype =    │            │
                            │ CHECKING     │            │
                            └──────────────┘            │
                                   └──────────┬─────────┘
                                              ▼
                              ┌─────────────────────────────────────────┐
                         N5   │ account = new BankAccount()             │
                              │ account.accountNumber = generateAccNum  │
                              │ account.iban = generateIBAN             │
                              │ account.balance = ZERO                  │
                              │ account.accountType = CLIENT            │
                              │ account.accountSubtype = subtype        │
                              │ account.user = user                     │
                              │ account.bank = bank                     │
                              │ return bankAccountRepository.save(acc)  │
                              └─────────────────────────────────────────┘
                                              │
                                           SORTIE
```

#### Enumération des chemins

| Chemin | Condition |
|--------|-----------|
| **P1** | Utilisateur introuvable |
| **P2** | Utilisateur trouvé, banque introuvable |
| **P3** | Utilisateur trouvé, banque trouvée, `subtype == null` → défaut CHECKING |
| **P4** | Utilisateur trouvé, banque trouvée, `subtype != null` (CHECKING ou SAVINGS) |

**Nombre total de chemins : 4**

#### Statement Coverage

| Statement | Couvert |
|-----------|---------|
| `user = findById(userId).orElseThrow(...)` |  |
| `throw "Utilisateur introuvable"` |  |
| `bank = findById(bankId).orElseThrow(...)` |  |
| `throw "Banque introuvable"` |  |
| `if (subtype == null)` |  (évalué à faux systématiquement dans les tests) |
| `subtype = AccountSubType.CHECKING` |  |
| Création et initialisation de `BankAccount` (7 setters) |  |
| `return bankAccountRepository.save(account)` |  |

**Statement Coverage = 10/13 ≈ 77%**

#### Branch Coverage

| Branche | Vrai | Faux |
|---------|:----:|:----:|
| user introuvable |  |  |
| bank introuvable |  |  |
| `subtype == null` |  |  |

**Branch Coverage = 3/6 = 50%**

#### Path Coverage

| Chemin | Test |
|--------|------|
| P1 |  Non couvert |
| P2 |  Non couvert |
| P3 |  Le branch `subtype == null` n'est jamais déclenché (surcharge fournit toujours CHECKING) |
| P4 |  `testCreateCheckingAccountExplicitly`, `testCreateSavingsAccount`, `testPath_UserCreatesCheckingAndSavingsAccounts` |

**Path Coverage = 1/4 = 25%**

---

### 3.4 `createInterbankAccount(Long bankOwnerId, Long linkedBankId)`

#### Graphe de Flot de Contrôle

```
         ┌──────────────────────────────────────────┐
         │ ENTRÉE  @Transactional                   │
         └──────────────────────────────────────────┘
                              │
                              ▼
         ┌──────────────────────────────────────────┐
    N1   │ bankOwner = bankRepository.findById      │
         │             (bankOwnerId)                │
         └──────────────────────────────────────────┘
                              │
              ◇ bankOwner présent? ◇
             /                    \
           Non                   Oui
            │                     │
  ══════════════════    ┌──────────────────────────────────────────┐
  T1: "Banque proprié-N2│ linkedBank = bankRepository.findById    │
   taire introuvable"  │              (linkedBankId)              │
                        └──────────────────────────────────────────┘
                                          │
                          ◇ linkedBank présent? ◇
                         /                     \
                       Non                    Oui
                        │                      │
           ═════════════════════   ◇ bankOwnerId == linkedBankId? ◇
           T2: "Banque liée       /                              \
            introuvable"        Oui                            Non
                                 │                               │
                        ══════════════════    ┌──────────────────────────────────────┐
                        T3: "Auto-référence"  │ account = new BankAccount()         │
                                              │ accountNumber = generateAccNum(...)  │
                                              │ iban = generateIBAN(...)             │
                                              │ balance = ZERO                       │
                                              │ accountType = INTERBANK              │
                                              │ accountSubtype = null                │
                                              │ bank = linkedBank                    │
                                              │ linkedBank = bankOwner               │
                                              │ return bankAccountRepository.save()  │
                                              └──────────────────────────────────────┘
                                                               │
                                                            SORTIE
```

#### Enumération des chemins

| Chemin | Condition |
|--------|-----------|
| **P1** | Banque propriétaire introuvable |
| **P2** | Propriétaire trouvé, banque liée introuvable |
| **P3** | Les deux trouvées, même ID → exception |
| **P4** | Les deux trouvées, IDs différents → succès |

**Nombre total de chemins : 4**

#### Statement Coverage

| Statement | Couvert |
|-----------|---------|
| `bankOwner = findById(bankOwnerId).orElseThrow()` |  |
| `throw "Banque propriétaire introuvable"` |  |
| `linkedBank = findById(linkedBankId).orElseThrow()` |  |
| `throw "Banque liée introuvable"` |  |
| `if (bankOwnerId.equals(linkedBankId))` |  |
| `throw "Auto-référence"` |  |
| Création et initialisation du compte INTERBANK (8 setters) |  |
| `return bankAccountRepository.save(account)` |  |

**Statement Coverage = 10/12 ≈ 83%**

#### Branch Coverage

| Branche | Vrai | Faux |
|---------|:----:|:----:|
| `bankOwner` introuvable |  |  |
| `linkedBank` introuvable |  |  |
| `bankOwnerId == linkedBankId` |  |  |

**Branch Coverage = 4/6 ≈ 67%**

#### Path Coverage

| Chemin | Test |
|--------|------|
| P1 |  |
| P2 |  |
| P3 |  `testCreateInterbankAccountSelfFail`, `testPath_InterbankAccountsNotAffected` (appel P4) |
| P4 |  `testCreateInterbankAccount`, `testTransferInterbank` |

**Path Coverage = 2/4 = 50%**

---

### 3.5 Méthodes de consultation (`getClientAccounts`, `getCheckingAccounts`, `getSavingsAccounts`, `getInterbankAccounts`, `getUserAccounts`, `getAllUserAccounts`)

Ces méthodes sont toutes des **pipelines stream linéaires sans branchement** (filtre `.filter()` sans décision explicite au niveau du flot de contrôle de la méthode).

```
         ┌─────────────────────────────────────────────────┐
         │ ENTRÉE                                          │
         └─────────────────────────────────────────────────┘
                               │
                               ▼
         ┌─────────────────────────────────────────────────┐
    N1   │ bankAccountRepository.findByBankId(bankId)      │
         │   .stream()                                     │
         │   .filter(acc → acc.getAccountType() == ...)    │
         │   .toList()                                     │
         └─────────────────────────────────────────────────┘
                               │
                            SORTIE
```

| Méthode | Tests couvrant | Statement | Branch | Path |
|---------|---------------|:---------:|:------:|:----:|
| `getClientAccounts` | `testGetClientAccounts`, `testMultipleUsersMultipleAccountSubtypes` |  100% | N/A | 100% |
| `getCheckingAccounts` | `testGetCheckingAccounts`, `testPath_MultipleUsersWithMultipleAccountTypes` |  100% | N/A | 100% |
| `getSavingsAccounts` | `testGetSavingsAccounts`, `testPath_MultipleUsersWithMultipleAccountTypes` |  100% | N/A | 100% |
| `getInterbankAccounts` |  Aucun test direct | 0% | N/A | 0% |
| `getUserAccounts` |  Aucun test direct | 0% | N/A | 0% |
| `getAllUserAccounts` | `testGetAllUserAccounts`, `testPath_UserAccessesAccountsAcrossBanks` |  100% | N/A | 100% |

---

### Synthèse BankService

| Méthode | Statement | Branch | Path |
|---------|:---------:|:------:|:----:|
| `createBank` | 60% | 50% | 33% (1/3) |
| `createClientAccount(3-arg)` | 77% | 50% | 25% (1/4) |
| `createClientAccount(2-arg)` | 100% | N/A | 100% |
| `createInterbankAccount` | 83% | 67% | 50% (2/4) |
| `getClientAccounts` | 100% | N/A | 100% |
| `getCheckingAccounts` | 100% | N/A | 100% |
| `getSavingsAccounts` | 100% | N/A | 100% |
| `getInterbankAccounts` | 0% | N/A | 0% |
| `getUserAccounts` | 0% | N/A | 0% |
| `getAllUserAccounts` | 100% | N/A | 100% |
| **TOTAL** | **≈ 72%** | **≈ 55%** | **≈ 61%** |

---

## 4. InterbankTransactionService

**Fichier :** `src/main/java/.../services/InterbankTransactionService.java`

### 4.1 Méthodes analysées

| # | Méthode |
|---|---------|
| 1 | `transferInterbank(fromIban, toIban, amount)` |
| 2 | `transferIntrabank(fromAccountNumber, toAccountNumber, amount)` |
| 3 | `deposit(iban, amount)` |
| 4 | `withdraw(iban, amount)` |
| 5 | `getHistory(iban)` |

---

### 4.2 `transferInterbank(String fromIban, String toIban, BigDecimal amount)`

> **Note importante :** L'ordre des vérifications diffère de `TransactionService.transfer()`. La validation des fonds insuffisants est effectuée **avant** la validation du montant. Cela constitue un défaut de conception : si le solde est supérieur à zéro mais que le montant est négatif ou nul, l'exception "Montant invalide" n'est atteinte que si le solde est suffisant.

#### Graphe de Flot de Contrôle

```
         ┌─────────────────────────────────────────────┐
         │ ENTRÉE  @Retry(dbRetry) @Transactional      │
         └─────────────────────────────────────────────┘
                              │
                              ▼
         ┌─────────────────────────────────────────────┐
    N1   │ from = bankAccountRepository                │
         │       .findByIbanForUpdate(fromIban)        │
         └─────────────────────────────────────────────┘
                              │
                  ◇ from présent? ◇
                 /                \
               Non               Oui
                │                 │
       ══════════════    ┌─────────────────────────────────────────────┐
       T1: "Compte   N2  │ to = bankAccountRepository                 │
        source int."     │      .findByIbanForUpdate(toIban)          │
                         └─────────────────────────────────────────────┘
                                           │
                               ◇ to présent? ◇
                              /                \
                            Non               Oui
                             │                 │
                 ════════════════   ◇ from.iban == to.iban? ◇
                 T2: "Compte dest." /                      \
                  introuvable"    Oui                     Non
                                   │                       │
                         ══════════════    ◇ balance < amount? ◇  ←  avant validation montant
                         T3: "Auto-       /                   \
                          transfert"    Oui                  Non
                                         │                    │
                               ══════════════    ◇ amount <= 0? ◇  ←  après fonds insuff.
                               T4: "Fonds ins." /             \
                                              Oui            Non
                                               │              │
                                     ══════════    ┌──────────────────────────────────────┐
                                     T5: "Montant N6│ from.balance -= amount             │
                                      invalide"    │ to.balance   += amount             │
                                                   │ debit tx (WITHDRAW, COMPLETED)     │
                                                   │ credit tx (DEPOSIT, COMPLETED)     │
                                                   │ save(from, to, debit, credit)      │
                                                   └──────────────────────────────────────┘
                                                                    │
                                                                 SORTIE
```

#### Enumération des chemins

| Chemin | Séquence | Condition |
|--------|----------|-----------|
| **P1** | N1 → T1 | Compte source introuvable |
| **P2** | N1 → N2 → T2 | Compte dest. introuvable |
| **P3** | N1 → N2 → D3(vrai) → T3 | Auto-transfert |
| **P4** | N1 → N2 → D3(faux) → D4(vrai) → T4 | Fonds insuffisants |
| **P5** | N1 → N2 → D3(faux) → D4(faux) → D5(vrai) → T5 | Montant invalide (balance ≥ amount ET amount ≤ 0) |
| **P6** | N1 → N2 → D3(faux) → D4(faux) → D5(faux) → N6 | Succès |

**Nombre total de chemins : 6**

#### Statement Coverage

| Statement | Couvert |
|-----------|---------|
| `from = findByIbanForUpdate(fromIban)` |  |
| `throw "Compte source introuvable"` |  (`pathInterbankFromMissing`) |
| `to = findByIbanForUpdate(toIban)` |  |
| `throw "Compte dest. introuvable"` |  (`pathInterbankToMissing`) |
| `if (from.iban == to.iban)` |  |
| `throw "Auto-transfert"` |  (`pathInterbankAutoTransferForbidden`) |
| `if (balance < amount)` |  |
| `throw "Fonds insuffisants"` |  (`pathInterbankInsufficientFunds`) |
| `if (amount <= 0)` |  |
| `throw "Montant invalide"` |  (`pathInterbankInvalidAmount`) |
| Mise à jour des soldes (2) |  |
| Création debit (6 setters) |  |
| Création credit (6 setters) |  |
| 4× `save(...)` |  |

**Statement Coverage ≈ 100%**

#### Branch Coverage

| Branche | Vrai | Faux |
|---------|:----:|:----:|
| `from` introuvable |  |  |
| `to` introuvable |  |  |
| `from.iban == to.iban` |  |  |
| `balance < amount` |  |  |
| `amount <= 0` |  |  |

**Branch Coverage = 10/10 = 100%**

#### Path Coverage

| Chemin | Test |
|--------|------|
| P1 |  `pathInterbankFromMissing` |
| P2 |  `pathInterbankToMissing` |
| P3 |  `pathInterbankAutoTransferForbidden` |
| P4 |  `pathInterbankInsufficientFunds` |
| P5 |  `pathInterbankInvalidAmount` (amount=0, balance=100 → balance≥amount → T5) |
| P6 |  `pathInterbankSuccess` |

**Path Coverage = 6/6 = 100%**

---

### 4.3 `transferIntrabank(String fromAccountNumber, String toAccountNumber, BigDecimal amount)`

> **Note :** Contrairement à `transferInterbank`, cette méthode **ne valide pas** que le montant est positif. Un montant de 0 ou négatif peut passer si le solde est supérieur.

#### Graphe de Flot de Contrôle

```
         ┌────────────────────────────────────────────────────┐
         │ ENTRÉE  @Retry(dbRetry) @Transactional             │
         └────────────────────────────────────────────────────┘
                              │
                              ▼
         ┌─────────────────────────────────────────────────────┐
    N1   │ from = findByAccountNumberForUpdate(fromAccountNum) │
         └─────────────────────────────────────────────────────┘
                              │
                  ◇ from présent? ◇
                 /                \
               Non               Oui
                │                 │
       ══════════════    ┌─────────────────────────────────────────────────────┐
       T1: "Compte   N2  │ to = findByAccountNumberForUpdate(toAccountNumber) │
        source int."     └─────────────────────────────────────────────────────┘
                                           │
                               ◇ to présent? ◇
                              /                \
                            Non               Oui
                             │                 │
                 ════════════════   ◇ from.accNum == to.accNum? ◇
                 T2: "Compte dest."  /                         \
                  introuvable"     Oui                        Non
                                    │                          │
                           ══════════════   ◇ balance < amount? ◇
                           T3: "Auto-      /                   \
                            transfert"   Oui                  Non
                                          │                    │
                                ══════════    ┌──────────────────────────────────┐
                                T4: "Fonds N5 │ from.balance -= amount          │
                                 insuff."    │ to.balance   += amount          │
                                             │ debit tx (WITHDRAW)             │
                                             │ credit tx (DEPOSIT)             │
                                             │ save(from, to, debit, credit)   │
                                             └──────────────────────────────────┘
                                                               │
                                                            SORTIE
```

#### Enumération des chemins

| Chemin | Condition |
|--------|-----------|
| **P1** | Compte source introuvable |
| **P2** | Compte dest. introuvable |
| **P3** | Auto-transfert |
| **P4** | Fonds insuffisants |
| **P5** | Succès (pas de validation du montant!) |

**Nombre total de chemins : 5**

#### Statement & Branch Coverage

| Statement | Couvert |
|-----------|---------|
| `from = findByAccountNumberForUpdate(fromAccountNumber)` |  |
| `throw "Compte source introuvable"` |  |
| `to = findByAccountNumberForUpdate(toAccountNumber)` |  |
| `throw "Compte dest. introuvable"` |  |
| `if (from.accNum == to.accNum)` |  |
| `throw "Auto-transfert"` |  |
| `if (balance < amount)` |  |
| `throw "Fonds insuffisants"` |  |
| Mise à jour des soldes + créations + saves |  |

**Statement Coverage = 7/11 ≈ 64%**

| Branche | Vrai | Faux |
|---------|:----:|:----:|
| `from` introuvable |  |  |
| `to` introuvable |  |  |
| auto-transfert |  |  |
| fonds insuffisants |  |  |

**Branch Coverage = 4/8 = 50%**

#### Path Coverage

| Chemin | Test |
|--------|------|
| P1 |  |
| P2 |  |
| P3 |  |
| P4 |  |
| P5 |  `pathIntrabankSuccess`, `testTransferIntrabank` |

**Path Coverage = 1/5 = 20%**

---

### 4.4 `deposit(String iban, BigDecimal amount)`

#### Graphe de Flot de Contrôle

```
         ┌──────────────────────────────────┐
         │ ENTRÉE  @Transactional           │
         └──────────────────────────────────┘
                          │
                          ▼
         ┌──────────────────────────────────┐
    N1   │ acc = findByIbanForUpdate(iban)  │
         └──────────────────────────────────┘
                          │
              ◇ acc présent? ◇
             /                \
           Non                Oui
            │                  │
     ══════════════   ◇ amount <= 0? ◇
     T1: "Compte      /             \
      introuvable"  Oui            Non
                     │              │
              ══════════  ┌────────────────────────────────────┐
              T2: "Montant│ acc.balance += amount             │
               invalide" │ tx.type = DEPOSIT                 │
                          │ save(acc), save(tx)               │
                          └────────────────────────────────────┘
                                          │
                                       SORTIE
```

#### Enumération des chemins

| Chemin | Condition |
|--------|-----------|
| **P1** | Compte introuvable |
| **P2** | Compte trouvé, montant ≤ 0 |
| **P3** | Succès |

**Nombre total de chemins : 3**

#### Coverage

| Métrique | Couverture | Détail |
|----------|:----------:|--------|
| Statement | 71% (5/7) | T1 (`throw "Compte introuvable"`) et T2 (`throw "Montant invalide"`) non couverts |
| Branch | 50% (2/4) | `acc` introuvable (vrai)  via `pathDepositMissingAccount`, mais montant ≤ 0  |
| Path | 67% (2/3) | P1 , P2 , P3  |

> Correction: `pathDepositMissingAccount` couvre P1 (compte introuvable). Le test `pathDepositWithdrawAndHistory` couvre P3. P2 (montant invalide pour dépôt) n'est pas testé.

**Path Coverage = 2/3 ≈ 67%**

---

### 4.5 `withdraw(String iban, BigDecimal amount)`

> **Même anomalie de conception que `transferInterbank`** : la vérification des fonds insuffisants précède la validation du montant. Ainsi, si `balance < 0` (impossible en pratique) ou si le solde est >= 0 mais le montant est négatif, on obtient "Montant invalide" seulement si les fonds sont suffisants.

#### Graphe de Flot de Contrôle

```
         ┌──────────────────────────────────┐
         │ ENTRÉE  @Transactional           │
         └──────────────────────────────────┘
                          │
                          ▼
         ┌──────────────────────────────────┐
    N1   │ acc = findByIbanForUpdate(iban)  │
         └──────────────────────────────────┘
                          │
              ◇ acc présent? ◇
             /                \
           Non                Oui
            │                  │
     ══════════════   ◇ balance < amount? ◇  ←  avant validation montant
     T1: "Compte       /                 \
      introuvable"   Oui                Non
                      │                  │
              ══════════════   ◇ amount <= 0? ◇  ←  après fonds insuff.
              T2: "Fonds       /              \
               insuff."      Oui            Non
                              │              │
                     ══════════  ┌─────────────────────────────────────┐
                     T3: "Montant│ acc.balance -= amount              │
                      invalide" │ tx.type = WITHDRAW                 │
                                 │ save(acc), save(tx)                │
                                 └─────────────────────────────────────┘
                                                │
                                             SORTIE
```

#### Enumération des chemins

| Chemin | Condition |
|--------|-----------|
| **P1** | Compte introuvable |
| **P2** | Compte trouvé, fonds insuffisants |
| **P3** | Compte trouvé, fonds suffisants, montant ≤ 0 |
| **P4** | Succès |

**Nombre total de chemins : 4**

#### Coverage

| Métrique | Couverture | Détail |
|----------|:----------:|--------|
| Statement | 78% (7/9) | `throw "Compte introuvable"` non couvert |
| Branch | 75% (6/8) | `acc` introuvable (vrai)  non couvert |
| Path | 75% (3/4) | P1 , P2  (`testInsufficientFunds`), P3  (`pathWithdrawInvalidAmount`), P4  (`pathDepositWithdrawAndHistory`) |

---

### 4.6 `getHistory(String iban)`

#### Graphe de Flot de Contrôle

```
         ┌─────────────────────────────────────────────────────────────┐
         │ ENTRÉE                                                      │
         └─────────────────────────────────────────────────────────────┘
                                     │
                                     ▼
         ┌─────────────────────────────────────────────────────────────┐
    N1   │ account = findByIban(iban).orElseThrow(...)                │
         └─────────────────────────────────────────────────────────────┘
                                     │
                         ◇ account présent? ◇
                        /                   \
                      Non                  Oui
                       │                    │
               ══════════════   ┌──────────────────────────────────────────────────────────┐
               T1: "Compte  N2  │ transactionRepository.findAll().stream()                │
                introuvable"    │   .filter(tx → sender.id==account.id                   │
                                │           || receiver.id==account.id)                  │
                                │   .sorted(by timestamp desc)                           │
                                │   .map(tx → TransactionResDTO{...})                    │
                                │   .toList()                                            │
                                └──────────────────────────────────────────────────────────┘
                                                        │
                                                     SORTIE
```

#### Chemins

| Chemin | Condition |
|--------|-----------|
| **P1** | Compte introuvable |
| **P2** | Succès (liste potentiellement vide ou non) |

**Nombre total de chemins : 2**

#### Coverage

| Métrique | Valeur | Test |
|----------|:------:|------|
| Statement | 75% (6/8) | T1 non couvert |
| Branch | 50% (1/2) | P1 non couvert |
| Path | 50% (1/2) | P2  `pathDepositWithdrawAndHistory` |

---

### Synthèse InterbankTransactionService

| Méthode | Statement | Branch | Path |
|---------|:---------:|:------:|:----:|
| `transferInterbank` | **100%** | **100%** | **100%** (6/6) |
| `transferIntrabank` | 64% | 50% | 20% (1/5) |
| `deposit` | 71% | 67% | 67% (2/3) |
| `withdraw` | 78% | 75% | 75% (3/4) |
| `getHistory` | 75% | 50% | 50% (1/2) |
| **TOTAL** | **≈ 78%** | **≈ 68%** | **≈ 63%** |

---

## 5. UserService

**Fichier :** `src/main/java/.../services/UserService.java`

### 5.1 Méthodes analysées

| # | Méthode |
|---|---------|
| 1 | `createUser(User newUser)` |
| 2 | `findAllUsers()` |
| 3 | `callBackApiUsing(User, Throwable)` |
| 4 | `callBackApiUsing(Throwable)` |

---

### 5.2 `createUser(User newUser)` — `@Async @RateLimiter`

#### Graphe de Flot de Contrôle

```
         ┌─────────────────────────────────────────────────────────────┐
         │ ENTRÉE                                                      │
         │ @Async("taskExecutor")                                      │
         │ @RateLimiter(name="userApi", fallback="callBackApiUsing")   │
         └─────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
                          ◇ newUser == null? ◇
                         /                   \
                        Oui                 Non
                         │                   │
               ══════════════    ┌────────────────────────────────────────────┐
               T1: Exception N2  │ savedUser = userRepository.save(newUser)  │
               "Utilisateur      └────────────────────────────────────────────┘
                vide"                               │
                                                    ▼
                                    ┌────────────────────────────────────────────┐
                               N3   │ banks = bankRepository.findAll()          │
                                    └────────────────────────────────────────────┘
                                                    │
                                       ◇ banks.isEmpty()? ◇
                                      /                   \
                                   Vide                 Non vide
                                     │                     │
                                     │        ┌────────────────────────────────────────────┐
                                     │   N5   │ defaultBank = banks.get(0)               │
                                     │        │ bankService.createClientAccount(           │
                                     │        │   savedUser.getId(),                       │
                                     │        │   defaultBank.getId(),                     │
                                     │        │   AccountSubType.CHECKING)                │
                                     │        └────────────────────────────────────────────┘
                                     └──────────────────────┬──────────────────────────────
                                                            │
                                    ┌────────────────────────────────────────────┐
                               N6   │ return CompletableFuture                  │
                                    │        .completedFuture(savedUser)        │
                                    └────────────────────────────────────────────┘
                                                            │
                                                         SORTIE
```

#### Enumération des chemins

| Chemin | Condition |
|--------|-----------|
| **P1** | `newUser == null` → Exception |
| **P2** | `newUser != null`, aucune banque en base → sauvegarde seul |
| **P3** | `newUser != null`, banque présente → sauvegarde + création compte CHECKING |

**Nombre total de chemins : 3**

#### Statement Coverage

| Statement | Couvert |
|-----------|---------|
| `if (newUser == null)` |  |
| `throw Exception("Utilisateur vide")` |  (`pathCreateUserNullInputThrowsException`) |
| `savedUser = userRepository.save(newUser)` |  |
| `banks = bankRepository.findAll()` |  |
| `if (!banks.isEmpty())` |  |
| `defaultBank = banks.get(0)` |  |
| `bankService.createClientAccount(...)` |  |
| `return CompletableFuture.completedFuture(savedUser)` |  |

**Statement Coverage = 8/8 = 100%**

#### Branch Coverage

| Branche | Vrai | Faux |
|---------|:----:|:----:|
| `newUser == null` |  |  |
| `banks.isEmpty()` |  |  |

**Branch Coverage = 4/4 = 100%**

#### Path Coverage

| Chemin | Test (intégration) | Test (unitaire) |
|--------|-------------------|-----------------|
| P1 | `pathCreateUserNullThrows`* |  `pathCreateUserNullInputThrowsException` |
| P2 |  `pathCreateUserWithoutBankDoesNotCreateAccount` |  `pathCreateUserWithoutDefaultBankSavesOnlyUser` |
| P3 |  `pathCreateUserWithDefaultBankCreatesAccount` |  `pathCreateUserWithDefaultBankCreatesClientBankAccount` |

> *P1 en intégration : le rate limiter intercepte avant d'atteindre la logique métier, d'où l'assertion sur le message du fallback. Le test unitaire (Mockito) couvre directement la logique.

**Path Coverage = 3/3 = 100%**

---

### 5.3 `findAllUsers()` — méthode linéaire

```
    ┌───────────────────────────────────────────┐
    │ return userRepository.findAll()           │
    └───────────────────────────────────────────┘
```

| Métrique | Valeur | Test |
|----------|:------:|------|
| Statement | 100% | `pathFindAllUsersReturnsExistingUsers`, `pathFindAllUsersReturnsRepositoryContent` |
| Branch | N/A | — |
| Path | 100% | Chemin unique couvert |

---

### 5.4 Méthodes de fallback

| Méthode | Statement | Test |
|---------|:---------:|------|
| `callBackApiUsing(User, Throwable)` — throw |  100% | Via rate limiter (intégration) |
| `callBackApiUsing(Throwable)` — return string |  100% | `pathRateLimiterFallbackReturnsExpectedMessage` |

---

### Synthèse UserService

| Méthode | Statement | Branch | Path |
|---------|:---------:|:------:|:----:|
| `createUser` | **100%** | **100%** | **100%** (3/3) |
| `findAllUsers` | 100% | N/A | 100% |
| `callBackApiUsing(User, Throwable)` | 100% | N/A | 100% |
| `callBackApiUsing(Throwable)` | 100% | N/A | 100% |
| **TOTAL** | **100%** | **100%** | **100%** |

---

## 6. Tableau de synthèse global

### 6.1 Par service

| Service | Statement | Branch | Path Coverage |
|---------|:---------:|:------:|:-------------:|
| `TransactionService` | ~65% | ~55% | ~29% |
| `BankService` | ~72% | ~55% | ~61% |
| `InterbankTransactionService` | ~78% | ~68% | ~63% |
| `UserService` | **100%** | **100%** | **100%** |
| **GLOBAL** | **~79%** | **~70%** | **~63%** |

### 6.2 Par méthode — tableau récapitulatif complet

| Service | Méthode | Paths total | Paths couverts | Path% | Branch% | Stmt% |
|---------|---------|:-----------:|:--------------:|:-----:|:-------:|:-----:|
| TransactionService | `transfer` | 6 | 1 | 17% | 50% | 74% |
| TransactionService | `depot` | 3 | 1 | 33% | 50% | 71% |
| TransactionService | `retrait` | 4 | 2 | 50% | 67% | 78% |
| TransactionService | `getHistory` | 1 | 0 | 0% | N/A | 0% |
| BankService | `createBank` | 3 | 1 | 33% | 50% | 60% |
| BankService | `createClientAccount(3)` | 4 | 1 | 25% | 50% | 77% |
| BankService | `createInterbankAccount` | 4 | 2 | 50% | 67% | 83% |
| BankService | `getInterbankAccounts` | 1 | 0 | 0% | N/A | 0% |
| BankService | `getUserAccounts` | 1 | 0 | 0% | N/A | 0% |
| InterbankTransactionService | `transferInterbank` | 6 | **6** | **100%** | **100%** | **100%** |
| InterbankTransactionService | `transferIntrabank` | 5 | 1 | 20% | 50% | 64% |
| InterbankTransactionService | `deposit` | 3 | 2 | 67% | 67% | 71% |
| InterbankTransactionService | `withdraw` | 4 | 3 | 75% | 75% | 78% |
| InterbankTransactionService | `getHistory` | 2 | 1 | 50% | 50% | 75% |
| UserService | `createUser` | 3 | **3** | **100%** | **100%** | **100%** |
| UserService | `findAllUsers` | 1 | 1 | 100% | N/A | 100% |

### 6.3 Chemins non couverts — récapitulatif critique

| Service | Méthode | Chemin manquant | Conséquence |
|---------|---------|-----------------|-------------|
| TransactionService | `transfer` | P1, P2 : comptes introuvables | L'exception `orElseThrow` n'est pas vérifiée |
| TransactionService | `transfer` | P3 : auto-transfert | Règle métier non testée |
| TransactionService | `transfer` | P4 : montant invalide | Montant ≤ 0 non testé |
| TransactionService | `transfer` | P5 : fonds insuffisants | Régression possible |
| TransactionService | `depot` | P1 : compte introuvable | Exception non vérifiée |
| TransactionService | `depot` | P2 : montant invalide | Montant ≤ 0 non testé |
| TransactionService | `retrait` | P1 : compte introuvable | Exception non vérifiée |
| TransactionService | `retrait` | P2 : montant invalide | Montant ≤ 0 non testé |
| TransactionService | `getHistory` | P1 : chemin unique | Méthode entièrement non testée |
| BankService | `createBank` | P1, P2 : null et SWIFT dupliqué | Validations non testées |
| BankService | `createClientAccount` | P1, P2, P3 | Not-found et subtype null non testés |
| BankService | `getInterbankAccounts` | unique | Méthode non testée |
| BankService | `getUserAccounts` | unique | Méthode non testée |
| InterbankTransactionService | `transferIntrabank` | P1–P4 | Seul le succès testé |
| InterbankTransactionService | `withdraw` | P1 : compte introuvable | Exception non vérifiée |
| InterbankTransactionService | `getHistory` | P1 : compte introuvable | Exception non vérifiée |

---

## 7. Observations et recommandations

### 7.1 Anomalie de conception — ordre de validation

Dans `InterbankTransactionService.transferInterbank()` et `withdraw()`, la vérification des fonds insuffisants est effectuée **avant** la validation du montant :

```
// Code actuel (problématique)
if (from.getBalance().compareTo(amount) < 0)  ← vérifié en 1er
    throw "Fonds insuffisants";

if (amount.compareTo(ZERO) <= 0)              ← vérifié en 2e
    throw "Montant invalide";
```

Le chemin P5 de `transferInterbank` (solde suffisant mais montant = 0) est fonctionnellement incohérent. L'ordre recommandé est : **valider le montant d'abord**, comme dans `TransactionService.transfer()`.

### 7.2 `transferIntrabank` — absence de validation du montant

`InterbankTransactionService.transferIntrabank()` ne valide **pas** que `amount > 0`. Un virement de 0 € est techniquement autorisé, créant deux transactions fantômes.

### 7.3 `TransactionService.getHistory()` — méthode non testée

Cette méthode est complètement absente de la suite de tests, alors que `InterbankTransactionService.getHistory()` est couverte.

### 7.4 Tests manquants prioritaires

Pour atteindre 80%+ de path coverage global, ajouter :

```java
// 1. transfer() — chemins manquants
@Test void transferShouldFailIfSourceAccountMissing()
@Test void transferShouldFailIfDestAccountMissing()
@Test void transferShouldFailForAutoTransfer()
@Test void transferShouldFailForInvalidAmount()
@Test void transferShouldFailForInsufficientFunds()

// 2. depot() / retrait() — chemins manquants
@Test void depotShouldFailIfAccountMissing()
@Test void depotShouldFailForInvalidAmount()
@Test void retraitShouldFailIfAccountMissing()
@Test void retraitShouldFailForInvalidAmount()

// 3. getHistory() dans TransactionService
@Test void getHistoryShouldReturnSortedTransactions()

// 4. createBank() — validations
@Test void createBankShouldFailForNullBank()
@Test void createBankShouldFailForDuplicateSwift()

// 5. createClientAccount — not found et subtype null
@Test void createClientAccountShouldFailForUnknownUser()
@Test void createClientAccountShouldFailForUnknownBank()
@Test void createClientAccountWithNullSubtypeShouldDefaultToChecking()

// 6. transferIntrabank — erreurs
@Test void intrabankShouldFailIfSourceMissing()
@Test void intrabankShouldFailIfDestMissing()
@Test void intrabankShouldFailForAutoTransfer()
@Test void intrabankShouldFailForInsufficientFunds()
```

### 7.5 Résumé de la qualité de la suite de tests

| Critère | État | Commentaire |
|---------|:----:|-------------|
| Tests d'intégration (Testcontainers) |  | Bonne couverture des cas nominaux |
| Tests unitaires (Mockito) |  | `UserServiceUnitPathTest` bien structuré |
| Tests de concurrence |  | `shouldHandleHighConcurrencyTransfers` |
| Couverture des cas d'erreur |  | Partielle — plusieurs exceptions non testées |
| Couverture de `transferInterbank` |  | **Excellente — 100% path coverage** |
| Couverture de `UserService` |  | **Excellente — 100% path coverage** |
| Couverture de `TransactionService.transfer` |  | **Faible — 17% path coverage** |
| Tests de `getInterbankAccounts` / `getUserAccounts` |  | Méthodes non couvertes |

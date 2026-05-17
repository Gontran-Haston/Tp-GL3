# Guide de Transformation - Système Bancaire Réel

## Vue d'ensemble

Notre système bancaire original était **simple et linéaire** :
```
Utilisateur → Compte → Transactions
```

Nous avons transformé cela en un **vrai système bancaire multi-institution** :
```
Banque
├── Comptes Clients (Account Type = CLIENT)
│   └── Propriétaire: User (particulier)
│
└── Comptes Inter-banques (Account Type = INTERBANK)
    └── Correspondance avec autre Banque
```

---

## Changements Clés Apportés

### 1. **Nouvelles Entités Créées**

#### `Bank.java` - Institution bancaire
```
- id: Long (clé primaire)
- name: String (unique) - "Banque de France"
- swiftCode: String (unique) - "BNFRFRPP" - Identifie la banque globalement
- country: String - "France"
- ibanPrefix: String - "FR" - Préfixe IBAN de la banque
- accounts: List<BankAccount> - Comptes opérés par cette banque
- linkedAccounts: List<BankAccount> - Comptes de correspondance
```

#### `BankAccount.java` - Compte bancaire polyvalent
```
- id: Long (clé primaire)
- iban: String (unique) - Format IBAN complet
- accountNumber: String (unique) - Format simplifié
- accountType: Enum (CLIENT ou INTERBANK)
- balance: BigDecimal - Solde du compte
- bank: Bank - Banque qui opère ce compte
- user: User (nullable) - Propriétaire si CLIENT
- linkedBank: Bank (nullable) - Si compte INTERBANK
- transactions: Historique
```

### 2. **Modifications d'Entités Existantes**

#### `Account.java` (DEPRECATED)
- Conservé pour rétrocompatibilité
- À remplacer progressivement par `BankAccount`

#### `Transaction.java` (AMÉLIORÉ)
```java
// ANCIEN (toujours présent)
- account: Account (deprecated)

// NOUVEAU (pour inter-banques)
- senderAccount: BankAccount
- receiverAccount: BankAccount
- transferStatus: String (PENDING|COMPLETED|FAILED)
```

#### `User.java` (ÉTENDU)
```java
// ANCIEN
- accounts: List<Account> (deprecated)

// NOUVEAU
- bankAccounts: List<BankAccount> - Comptes dans les différentes banques
```

---

## Repositories Créés

### `BankRepository`
```java
- findBySwiftCode(code) - Rechercher banque par SWIFT
- findByName(name) - Rechercher par nom
- findByCountry(country) - Lister banques par pays
```

### `BankAccountRepository`
```java
- findByIbanForUpdate(iban) - Verrouillage pessimiste
- findByAccountNumberForUpdate(number)
- findByUserId(userId) - Comptes d'un client
- findByBankId(bankId) - Comptes d'une banque
- findByBankIdAndAccountType(bankId, type)
- findByLinkedBankId(bankId) - Comptes de correspondance
```

---

## Services Créés

### `BankService.java`
**Gestion complète des banques et comptes**

| Méthode | Fonction |
|---------|----------|
| `createBank()` | Créer une nouvelle banque |
| `createClientAccount()` | Ouvrir compte client (particulier) |
| `createInterbankAccount()` | Ouvrir compte inter-banque (correspondance) |
| `getClientAccounts()` | Lister comptes clients d'une banque |
| `getInterbankAccounts()` | Lister comptes de correspondance |
| `getAllUserAccounts()` | Lister tous les comptes d'un client |

### `InterbankTransactionService.java`
**Transactions modernes avec support inter-banques**

| Méthode | Fonction |
|---------|----------|
| `transferInterbank()` | Virement IBAN vers IBAN (inter-banques) |
| `transferIntrabank()` | Virement intra-banque (même banque) |
| `deposit()` | Dépôt sur compte |
| `withdraw()` | Retrait depuis compte |
| `getHistory()` | Historique des transactions |

**Caractéristiques:**
- Verrouillage pessimiste (concurrence)
- Transactions atomiques
- Gestion des erreurs
- Références uniques pour tracer virements

---

## API Créées

### **Endpoints Banques** (`/api/banks`)

```
POST   /api/banks                              - Créer banque
GET    /api/banks                              - Lister toutes les banques
GET    /api/banks/{swiftCode}                  - Récupérer une banque
POST   /api/banks/{bankId}/client-account      - Ouvrir compte client
POST   /api/banks/{bankOwnerId}/interbank-account/{linkedBankId}  - Ouvrir compte inter-banque
GET    /api/banks/{bankId}/client-accounts     - Lister comptes clients
GET    /api/banks/{bankId}/interbank-accounts  - Lister comptes de correspondance
```

### **Endpoints Transactions** (`/api/interbank-transactions`)

```
POST   /api/interbank-transactions/transfer           - Virement IBAN
POST   /api/interbank-transactions/transfer-by-account - Virement par numéro
POST   /api/interbank-transactions/deposit            - Dépôt
POST   /api/interbank-transactions/withdraw           - Retrait
GET    /api/interbank-transactions/history/{iban}     - Historique
```

---

## Schéma de Données

### Relations:
```
Bank (1) ──── (N) BankAccount
  ├─ Accounts propres
  └─ Accounts de correspondance

User (1) ──── (N) BankAccount
  └─ Comptes clients

BankAccount (1) ──── (N) Transaction
  ├─ Transactions envoyées (sender)
  └─ Transactions reçues (receiver)
```
[!Representation de la BD](modele.png)

### Exemple SQL:
```sql
CREATE TABLE banks (
  id BIGINT PRIMARY KEY,
  name VARCHAR UNIQUE,
  swift_code VARCHAR(8) UNIQUE,
  country VARCHAR,
  iban_prefix VARCHAR(2)
);

CREATE TABLE bank_accounts (
  id BIGINT PRIMARY KEY,
  iban VARCHAR UNIQUE,
  account_number VARCHAR UNIQUE,
  account_type ENUM('CLIENT', 'INTERBANK'),
  balance DECIMAL,
  bank_id BIGINT REFERENCES banks(id),
  user_id BIGINT REFERENCES users(id),
  linked_bank_id BIGINT REFERENCES banks(id),
  version BIGINT
);

CREATE TABLE transactions (
  id BIGINT PRIMARY KEY,
  amount DECIMAL,
  timestamp DATETIME,
  type ENUM('DEPOSIT', 'WITHDRAW'),
  sender_account_id BIGINT REFERENCES bank_accounts(id),
  receiver_account_id BIGINT REFERENCES bank_accounts(id),
  transfer_status VARCHAR,
  description VARCHAR,
  reference VARCHAR
);

CREATE TABLE users (
  id BIGINT PRIMARY KEY,
  email VARCHAR,
  first_name VARCHAR,
  last_name VARCHAR
);
```

---

## 🔄 Exemples d'Utilisation

### Créer une banque:
```json
POST /api/banks
{
  "name": "BNP Paribas",
  "swiftCode": "BNPAPFRP",
  "country": "France",
  "ibanPrefix": "FR"
}
```

### Ouvrir un compte client:
```
POST /api/banks/1/client-account
Content-Type: application/json
5
```
Résultat: Compte CLIENT lié à User(5) chez Bank(1)

### Ouvrir compte de correspondance:
```
POST /api/banks/1/interbank-account/2
```
Résultat: Bank(1) ouvre un compte INTERBANK chez Bank(2)

### Effectuer un virement inter-banques:
```json
POST /api/interbank-transactions/transfer
{
  "fromIban": "FR76123456789012345678901234",
  "toIban": "DE89370400440532013000",
  "amount": 1000
}
```
Bien vouloir ouvrir swagger pour mieux analyser les endpoints.
---

## Avantages du Nouveau Système

| Avant | Après |
|-------|-------|
| Pas de banques | Multiples institutions bancaires |
| Comptes isolés | Réseau de correspondance |
| Transferts simples | Virements inter-banques |
| Pas d'IBAN | Standards IBAN/SWIFT |
| Pas de concurrence | Verrouillage pessimiste |
| Peu de traçabilité | Statut transfert, références |


Vous pourrez tester le système avec les exemples de données
se trouvant dans le fichier src/main/resources/db/migration/*.sql

---

## Conclusion

Notre système est maintenant un **vrai système bancaire** capable de:
- Gérer plusieurs institutions bancaires
- Supporter comptes clients ET inter-banques
- Effectuer virements inter-banques avec traçabilité
- Utiliser standards IBAN/SWIFT internationaux
- Gérer concurrence avec verrouillage pessimiste
- Maintenir rétrocompatibilité avec ancien code
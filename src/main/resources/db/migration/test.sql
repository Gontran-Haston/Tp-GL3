
DELETE FROM transactions;
DELETE FROM bank_accounts;
DELETE FROM users;
DELETE FROM banks;

-- =========================================================
-- INSERTION DES BANQUES
-- IDs AUTO-INCREMENT => NON FOURNIS
-- =========================================================

INSERT INTO banks (name, swift_code, country, iban_prefix)
VALUES
('BNP Paribas', 'BNPAFRPP', 'France', 'FR'),
('Société Générale', 'SOGEFRPP', 'France', 'FR'),
('Deutsche Bank', 'DEUTDEFF', 'Allemagne', 'DE'),
('JP Morgan Chase', 'CHASUS33', 'États-Unis', 'US');

-- =========================================================
-- INSERTION DES UTILISATEURS
-- IDs AUTO-INCREMENT => NON FOURNIS
-- =========================================================

INSERT INTO users (first_name, last_name, email)
VALUES
('Jean', 'Dupont', 'jean.dupont@gmail.com'),
('Marie', 'Martin', 'marie.martin@gmail.com'),
('Paul', 'Durand', 'paul.durand@gmail.com'),
('Sophie', 'Bernard', 'sophie.bernard@gmail.com'),
('Lucas', 'Petit', 'lucas.petit@gmail.com');

-- =========================================================
-- INSERTION DES COMPTES CLIENTS
-- Hypothèse :
-- banques IDs = 16 à 19
-- users IDs = 16 à 20
-- bank_accounts IDs générés automatiquement
-- =========================================================

INSERT INTO bank_accounts (
    iban,
    account_number,
    account_type,
    account_subtype,
    balance,
    bank_id,
    user_id,
    linked_bank_id,
    version
)
VALUES

-- Jean Dupont
(
    'FR7611111111111111111111111',
    'ACC-100001',
    'CLIENT',
    'CHECKING',
    5200.50,
    16,
    16,
    NULL,
    0
),

-- Marie Martin
(
    'FR7622222222222222222222222',
    'ACC-100002',
    'CLIENT',
    'SAVINGS',
    12000.00,
    16,
    17,
    NULL,
    0
),

-- Paul Durand
(
    'FR7633333333333333333333333',
    'ACC-100003',
    'CLIENT',
    'CHECKING',
    3500.75,
    17,
    18,
    NULL,
    0
),

-- Sophie Bernard
(
    'DE4444444444444444444444444',
    'ACC-100004',
    'CLIENT',
    'SAVINGS',
    8450.00,
    18,
    19,
    NULL,
    0
),

-- Lucas Petit
(
    'US5555555555555555555555555',
    'ACC-100005',
    'CLIENT',
    'CHECKING',
    1500.25,
    19,
    20,
    NULL,
    0
);

-- =========================================================
-- INSERTION DES COMPTES INTER-BANQUES
-- Hypothèse :
-- comptes générés = IDs 21 à 28
-- =========================================================

INSERT INTO bank_accounts (
    iban,
    account_number,
    account_type,
    account_subtype,
    balance,
    bank_id,
    user_id,
    linked_bank_id,
    version
)
VALUES

-- Compte BNP chez Société Générale
(
    'FR9999999999999999999999991',
    'INT-200001',
    'INTERBANK',
    NULL,
    1000000.00,
    16,
    NULL,
    17,
    0
),

-- Compte Société Générale chez Deutsche Bank
(
    'DE8888888888888888888888888',
    'INT-200002',
    'INTERBANK',
    NULL,
    750000.00,
    17,
    NULL,
    18,
    0
),

-- Compte Deutsche Bank chez JP Morgan
(
    'US7777777777777777777777777',
    'INT-200003',
    'INTERBANK',
    NULL,
    500000.00,
    18,
    NULL,
    19,
    0
);

-- =========================================================
-- INSERTION DES TRANSACTIONS
-- Hypothèse :
-- bank_accounts IDs :
-- 16 -> Jean
-- 17 -> Marie
-- 18 -> Paul
-- 19 -> Sophie
-- 20 -> Lucas
-- =========================================================

INSERT INTO transactions (
    amount,
    timestamp,
    type,
    sender_account_id,
    receiver_account_id,
    description,
    reference,
    transfer_status
)
VALUES

-- Virement Jean -> Marie
(
    250.00,
    '2025-05-01 10:15:00',
    'VIREMENT',
    16,
    17,
    'Paiement facture',
    'TXN-000001',
    'COMPLETED'
),

-- Virement Marie -> Paul
(
    500.00,
    '2025-05-02 14:30:00',
    'VIREMENT',
    17,
    18,
    'Remboursement prêt',
    'TXN-000002',
    'COMPLETED'
),

-- Dépôt sur compte Sophie
(
    1200.00,
    '2025-05-03 09:45:00',
    'DEPOT',
    NULL,
    19,
    'Versement salaire',
    'TXN-000003',
    'COMPLETED'
),

-- Retrait Lucas
(
    300.00,
    '2025-05-04 16:10:00',
    'RETRAIT',
    20,
    NULL,
    'Retrait distributeur',
    'TXN-000004',
    'COMPLETED'
),

-- Virement international
(
    1000.00,
    '2025-05-05 11:20:00',
    'VIREMENT',
    16,
    19,
    'Paiement international',
    'TXN-000005',
    'PENDING'
);
-- ============================================================
-- Migration: Transformation du système bancaire
-- Objectif: Passer d'un système simple à un système multi-banques
-- ============================================================

-- 1. Créer la table BANKS
CREATE TABLE banks (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    swift_code VARCHAR(8) NOT NULL UNIQUE,
    country VARCHAR(100) NOT NULL,
    iban_prefix VARCHAR(2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Créer la table BANK_ACCOUNTS (remplace partiellement ACCOUNTS)
CREATE TABLE bank_accounts (
    id BIGSERIAL PRIMARY KEY,
    iban VARCHAR(34) NOT NULL UNIQUE,
    account_number VARCHAR(255) NOT NULL UNIQUE,
    account_type VARCHAR(20) NOT NULL CHECK (account_type IN ('CLIENT', 'INTERBANK')),
    balance DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    bank_id BIGINT NOT NULL,
    user_id BIGINT,
    linked_bank_id BIGINT,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (bank_id) REFERENCES banks(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY (linked_bank_id) REFERENCES banks(id) ON DELETE SET NULL,
    INDEX idx_iban (iban),
    INDEX idx_account_number (account_number),
    INDEX idx_bank_id (bank_id),
    INDEX idx_user_id (user_id),
    INDEX idx_linked_bank_id (linked_bank_id),
    INDEX idx_account_type (account_type)
);

-- 3. Modifier la table TRANSACTIONS pour supporter inter-banques
-- Note: Cette migration est additive, ne supprime rien

ALTER TABLE transactions 
ADD COLUMN sender_account_id BIGINT,
ADD COLUMN receiver_account_id BIGINT,
ADD COLUMN transfer_status VARCHAR(20),
ADD FOREIGN KEY (sender_account_id) REFERENCES bank_accounts(id) ON DELETE SET NULL,
ADD FOREIGN KEY (receiver_account_id) REFERENCES bank_accounts(id) ON DELETE SET NULL,
ADD INDEX idx_sender_account_id (sender_account_id),
ADD INDEX idx_receiver_account_id (receiver_account_id),
ADD INDEX idx_transfer_status (transfer_status);

-- 4. Créer table de liaison pour les comptes inter-banques
-- Optionnel: pour tracker les relations de correspondance
CREATE TABLE bank_correspondence (
    id BIGSERIAL PRIMARY KEY,
    source_bank_id BIGINT NOT NULL,
    target_bank_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    established_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (source_bank_id) REFERENCES banks(id) ON DELETE CASCADE,
    FOREIGN KEY (target_bank_id) REFERENCES banks(id) ON DELETE CASCADE,
    FOREIGN KEY (account_id) REFERENCES bank_accounts(id) ON DELETE CASCADE,
    UNIQUE KEY uk_correspondence (source_bank_id, target_bank_id),
    INDEX idx_source_bank (source_bank_id),
    INDEX idx_target_bank (target_bank_id)
);

-- 5. Données de test: Insérer banques exemple

INSERT INTO banks (name, swift_code, country, iban_prefix) VALUES
('BNP Paribas', 'BNPAPFRP', 'France', 'FR'),
('Société Générale', 'SOGEDEFF', 'France', 'FR'),
('Deutsche Bank', 'DEUTDEFF', 'Germany', 'DE'),
('ING Bank', 'INGBDEDN', 'Germany', 'DE'),
('HSBC', 'MIDLGB22', 'United Kingdom', 'GB');

-- 6. Créer des comptes inter-banques de test
-- BNP Paribas ouvre un compte chez Société Générale
INSERT INTO bank_accounts 
(iban, account_number, account_type, balance, bank_id, linked_bank_id)
VALUES
('FR7630001000010000000000001', 'ACC-BNPAPFRP-001', 'INTERBANK', 1000000.00, 2, 1);

-- Deutsche Bank ouvre compte chez HSBC
INSERT INTO bank_accounts
(iban, account_number, account_type, balance, bank_id, linked_bank_id)
VALUES
('DE89370400440532013001', 'ACC-DEUTDEFF-001', 'INTERBANK', 1500000.00, 5, 3);

-- 7. Indexes supplémentaires pour performance
CREATE INDEX idx_transactions_timestamp ON transactions(timestamp DESC);
CREATE INDEX idx_transactions_reference ON transactions(reference);
CREATE INDEX idx_bank_accounts_balance ON bank_accounts(balance);

-- 8. Vues utiles
CREATE VIEW v_bank_liquidity AS
SELECT 
    b.name as bank_name,
    b.swift_code,
    COUNT(ba.id) as total_accounts,
    SUM(CASE WHEN ba.account_type = 'CLIENT' THEN 1 ELSE 0 END) as client_accounts,
    SUM(CASE WHEN ba.account_type = 'INTERBANK' THEN 1 ELSE 0 END) as interbank_accounts,
    SUM(ba.balance) as total_liquidity
FROM banks b
LEFT JOIN bank_accounts ba ON b.id = ba.bank_id
GROUP BY b.id, b.name, b.swift_code;

CREATE VIEW v_user_total_balance AS
SELECT
    u.id,
    u.first_name,
    u.last_name,
    u.email,
    COUNT(DISTINCT ba.bank_id) as banks_count,
    COUNT(ba.id) as total_accounts,
    SUM(ba.balance) as total_balance
FROM users u
LEFT JOIN bank_accounts ba ON u.id = ba.user_id AND ba.account_type = 'CLIENT'
GROUP BY u.id, u.first_name, u.last_name, u.email;

-- 9. Procedures utiles

DELIMITER //

-- Procedure: Créer compte client dans une banque
CREATE PROCEDURE sp_create_client_account(
    IN p_user_id BIGINT,
    IN p_bank_id BIGINT,
    IN p_iban VARCHAR(34),
    IN p_account_number VARCHAR(255),
    OUT p_account_id BIGINT
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SET p_account_id = 0;
    END;
    
    START TRANSACTION;
    
    INSERT INTO bank_accounts 
    (iban, account_number, account_type, balance, bank_id, user_id)
    VALUES
    (p_iban, p_account_number, 'CLIENT', 0.00, p_bank_id, p_user_id);
    
    SET p_account_id = LAST_INSERT_ID();
    COMMIT;
END //

-- Procedure: Effectuer virement inter-banques
CREATE PROCEDURE sp_transfer_interbank(
    IN p_from_iban VARCHAR(34),
    IN p_to_iban VARCHAR(34),
    IN p_amount DECIMAL(19,2),
    IN p_description VARCHAR(255),
    OUT p_success BOOLEAN
)
BEGIN
    DECLARE v_from_id BIGINT;
    DECLARE v_to_id BIGINT;
    DECLARE v_from_balance DECIMAL(19,2);
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SET p_success = FALSE;
    END;
    
    START TRANSACTION;
    
    SELECT id, balance INTO v_from_id, v_from_balance
    FROM bank_accounts WHERE iban = p_from_iban FOR UPDATE;
    
    SELECT id INTO v_to_id
    FROM bank_accounts WHERE iban = p_to_iban FOR UPDATE;
    
    IF v_from_id IS NULL OR v_to_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Compte introuvable';
    END IF;
    
    IF v_from_balance < p_amount THEN
        SIGNAL SQLSTATE '45001' SET MESSAGE_TEXT = 'Fonds insuffisants';
    END IF;
    
    UPDATE bank_accounts SET balance = balance - p_amount WHERE id = v_from_id;
    UPDATE bank_accounts SET balance = balance + p_amount WHERE id = v_to_id;
    
    INSERT INTO transactions
    (amount, timestamp, type, sender_account_id, receiver_account_id, transfer_status, description, reference)
    VALUES
    (p_amount, NOW(), 'WITHDRAW', v_from_id, v_to_id, 'COMPLETED', p_description, UUID());
    
    INSERT INTO transactions
    (amount, timestamp, type, sender_account_id, receiver_account_id, transfer_status, description, reference)
    VALUES
    (p_amount, NOW(), 'DEPOSIT', v_from_id, v_to_id, 'COMPLETED', p_description, UUID());
    
    COMMIT;
    SET p_success = TRUE;
END //

DELIMITER ;

-- 10. Cleanup anciens objets (à faire progressivement)
-- ALTER TABLE accounts MODIFY COLUMN user_id BIGINT;
-- -- Puis archiver et supprimer après migration complète

COMMIT;

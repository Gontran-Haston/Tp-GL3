-- ============================================================
-- Migration: Ajouter les sous-types de comptes (SAVINGS, CHECKING)
-- Objectif: Permettre aux clients d'avoir des comptes d'épargne et courants
-- ============================================================

-- 1. Ajouter la colonne pour le sous-type de compte client
ALTER TABLE bank_accounts 
ADD COLUMN account_subtype VARCHAR(20) DEFAULT 'CHECKING',
ADD INDEX idx_account_subtype (account_subtype);

-- 2. Mettre à jour la contrainte de vérification
-- Pour MySQL: DROP et recréer la contrainte
ALTER TABLE bank_accounts 
DROP CHECK IF EXISTS bank_accounts_chk_1;

-- 3. Ajouter une nouvelle contrainte plus permissive
ALTER TABLE bank_accounts 
ADD CONSTRAINT account_type_check 
CHECK (account_type IN ('CLIENT', 'INTERBANK', 'SAVINGS', 'CHECKING'));

-- 4. Documenter les types de comptes:
-- CLIENT: Compte client générique
-- INTERBANK: Compte de correspondance avec une autre banque
-- SAVINGS: Compte d'épargne d'un client
-- CHECKING: Compte courant d'un client
-- account_subtype: CHECKING (défaut) ou SAVINGS pour les comptes CLIENT

-- 5. Créer une vue pour faciliter les requêtes par type de compte
CREATE OR REPLACE VIEW v_client_savings_accounts AS
SELECT * FROM bank_accounts 
WHERE account_type = 'CLIENT' AND account_subtype = 'SAVINGS';

CREATE OR REPLACE VIEW v_client_checking_accounts AS
SELECT * FROM bank_accounts 
WHERE account_type = 'CLIENT' AND account_subtype = 'CHECKING';

-- 6. Index pour recherche rapide par type de sous-compte
CREATE INDEX idx_user_account_type ON bank_accounts(user_id, account_subtype) 
WHERE account_type = 'CLIENT';

-- 7. Ajouter des commentaires descriptifs sur les nouvelles colonnes
COMMENT ON COLUMN bank_accounts.account_type IS 'Type principal: CLIENT, INTERBANK, SAVINGS ou CHECKING';
COMMENT ON COLUMN bank_accounts.account_subtype IS 'Sous-type pour les comptes clients: CHECKING (défaut) ou SAVINGS';

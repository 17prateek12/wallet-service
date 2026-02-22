CREATE TABLE asset_types (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL
);

CREATE TABLE wallets (
    id SERIAL PRIMARY KEY,
    user_id INT,
    asset_type_id INT NOT NULL,
    created_at TIMESTAMP DEFAULT now(),
    FOREIGN KEY (asset_type_id) REFERENCES asset_types(id)
);

CREATE TABLE transactions (
    id SERIAL PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(200) UNIQUE NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT now()
);

CREATE TABLE ledger_entries (
    id SERIAL PRIMARY KEY,
    transaction_id INT NOT NULL,
    wallet_id INT NOT NULL,
    amount BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT now(),
    FOREIGN KEY (transaction_id) REFERENCES transactions(id),
    FOREIGN KEY (wallet_id) REFERENCES wallets(id)
);

-- Seed asset types
INSERT INTO asset_types (name) VALUES ('Gold Coins');

-- System Wallets
INSERT INTO wallets (user_id, asset_type_id) VALUES (NULL, 1); -- Treasury
INSERT INTO wallets (user_id, asset_type_id) VALUES (NULL, 1); -- Bonus Pool
INSERT INTO wallets (user_id, asset_type_id) VALUES (NULL, 1); -- Revenue

-- Users
INSERT INTO wallets (user_id, asset_type_id) VALUES (1, 1);
INSERT INTO wallets (user_id, asset_type_id) VALUES (2, 1);
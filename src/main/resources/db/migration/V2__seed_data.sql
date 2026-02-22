-- Additional asset types (assignment: e.g. Gold Coins, Diamonds, Loyalty Points)
INSERT INTO asset_types (name) VALUES ('Diamonds'), ('Loyalty Points');

-- Initial balances for the two users (Bonus Pool wallet_id=2 -> User wallets 4 and 5)
-- User 1: 500 Gold Coins
INSERT INTO transactions (type, idempotency_key, status) VALUES ('BONUS', 'seed-user1-initial-balance', 'SUCCESS');
INSERT INTO ledger_entries (transaction_id, wallet_id, amount) VALUES (currval('transactions_id_seq'), 2, -500), (currval('transactions_id_seq'), 4, 500);

-- User 2: 300 Gold Coins
INSERT INTO transactions (type, idempotency_key, status) VALUES ('BONUS', 'seed-user2-initial-balance', 'SUCCESS');
INSERT INTO ledger_entries (transaction_id, wallet_id, amount) VALUES (currval('transactions_id_seq'), 2, -300), (currval('transactions_id_seq'), 5, 300);

--liquibase formatted sql

--changeset privatbank-task:demo-data context:demo
INSERT INTO wallet (id, owner_id, currency, balance)
VALUES (1, 'alice', 'UAH', 10000.00),
       (2, 'bob', 'UAH', 5000.00),
       (3, 'carol', 'UAH', 2500.00),
       (4, 'dave', 'UAH', 300.00),
       (5, 'alice', 'USD', 1000.00);

INSERT INTO payment (id, type, status, source_wallet_id, target_wallet_id, amount, currency,
                     idempotency_key, created_by, created_at, confirmed_by, completed_at)
VALUES (1, 'TOP_UP', 'COMPLETED', NULL, 1, 10000.00, 'UAH', 'demo-initial-top-up-1', 'system', now(), 'system', now()),
       (2, 'TOP_UP', 'COMPLETED', NULL, 2, 5000.00, 'UAH', 'demo-initial-top-up-2', 'system', now(), 'system', now()),
       (3, 'TOP_UP', 'COMPLETED', NULL, 3, 2500.00, 'UAH', 'demo-initial-top-up-3', 'system', now(), 'system', now()),
       (4, 'TOP_UP', 'COMPLETED', NULL, 4, 300.00, 'UAH', 'demo-initial-top-up-4', 'system', now(), 'system', now()),
       (5, 'TOP_UP', 'COMPLETED', NULL, 5, 1000.00, 'USD', 'demo-initial-top-up-5', 'system', now(), 'system', now());

INSERT INTO funding_request (id, source_wallet_id, target_wallet_id, currency, status)
VALUES (1, 1, 2, 'UAH', 'APPROVED'),
       (2, 2, 3, 'UAH', 'APPROVED'),
       (3, 4, 1, 'UAH', 'APPROVED');

INSERT INTO funding_schedule_item (id, funding_request_id, due_date, amount)
VALUES (1, 1, CURRENT_DATE - 1, 1000.00),
       (2, 1, CURRENT_DATE + 30, 1000.00),
       (3, 1, CURRENT_DATE + 60, 1000.00),
       (4, 2, CURRENT_DATE + 7, 500.00),
       (5, 2, CURRENT_DATE + 37, 500.00),
       (6, 3, CURRENT_DATE, 1000.00);

SELECT setval(pg_get_serial_sequence('wallet', 'id'), (SELECT max(id) FROM wallet));
SELECT setval(pg_get_serial_sequence('payment', 'id'), (SELECT max(id) FROM payment));
SELECT setval(pg_get_serial_sequence('funding_request', 'id'), (SELECT max(id) FROM funding_request));
SELECT setval(pg_get_serial_sequence('funding_schedule_item', 'id'), (SELECT max(id) FROM funding_schedule_item));
--rollback DELETE FROM funding_schedule_item WHERE id IN (1, 2, 3, 4, 5, 6);
--rollback DELETE FROM funding_request WHERE id IN (1, 2, 3);
--rollback DELETE FROM payment WHERE id IN (1, 2, 3, 4, 5);
--rollback DELETE FROM wallet WHERE id IN (1, 2, 3, 4, 5);

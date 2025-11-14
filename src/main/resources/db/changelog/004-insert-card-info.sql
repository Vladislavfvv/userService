--liquibase formatted sql
--preconditions onFail:HALT onError:HALT
--changeset Filimontsev_VV:004

INSERT INTO card_info (user_id, number, holder, expiration_date)
VALUES
    (1, '1111 2222 3333 4444', 'Ivan Ivanov', '2028-05-31'),
    (1, '5555 6666 7777 8888', 'Ivan Ivanov', '2025-12-31'),
    (2, '7777 8888 9999 0000', 'Petr Petrov', '2026-03-31');

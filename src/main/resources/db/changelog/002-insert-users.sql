--liquibase formatted sql
--preconditions onFail:HALT onError:HALT
--changeset Filimontsev_VV:002
INSERT INTO users (name, surname, birth_date, email)
VALUES
    ('Ivan', 'Ivanov', '1990-01-01', 'ivanov@example.com'),
    ('Petr', 'Petrov', '1985-03-12', 'petrov@example.com'),
    ('Anton', 'Sidorov', '1980-01-01', 'petroff@example.com');

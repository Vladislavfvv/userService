-- Подключаемся к нужной базе
\connect us_db;

-- Создаём схему, если она не существует
-- Схема public используется по умолчанию в PostgreSQL
-- Все таблицы создаются в схеме public
-- CREATE SCHEMA IF NOT EXISTS public AUTHORIZATION postgres;

-- Даём все права пользователю postgres
-- Схема public доступна всем пользователям по умолчанию
-- GRANT ALL ON SCHEMA public TO postgres;
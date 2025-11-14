-- Подключаемся к нужной базе
\connect us_db;

-- Создаём схему, если она не существует
CREATE SCHEMA IF NOT EXISTS userservice_data AUTHORIZATION postgres;

-- Даём все права пользователю postgres
GRANT ALL ON SCHEMA userservice_data TO postgres;
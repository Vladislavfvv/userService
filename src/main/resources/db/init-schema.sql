-- \connect us_db;
--
-- CREATE SCHEMA IF NOT EXISTS userService_data;
\connect us_db;

CREATE SCHEMA IF NOT EXISTS userservice_data AUTHORIZATION postgres;
GRANT ALL ON SCHEMA userservice_data TO postgres;
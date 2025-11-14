-- Ensure we are operating within the target database
\connect us_db;

-- Seed default administrator profile for user-service domain model
INSERT INTO userservice_data.users (name, surname, birth_date, email)
VALUES ('Admin', 'User', NULL, 'admin@tut.by')
ON CONFLICT (email) DO NOTHING;


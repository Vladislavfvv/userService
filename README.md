# UserService project

!!!This app run with Docker

### Prerequisites
- Docker and Docker Compose installed

### Steps
1. Clone the repository

2. Create '.env' file in the project root.

3. Write in '.env' file:
   POSTGRES_DB=us_db
   POSTGRES_USER=postgres
   POSTGRES_PASSWORD=postgres

4. Run containers:
docker compose up --build

5. Access the application at:
http://localhost:8082
PostgreSQL: localhost:5432
Redis: localhost:6379

e.g. http://localhost:8082/api/v1/users
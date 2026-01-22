# agent-framework
Spring Boot starter project for the agent framework.

## Requirements
- Java 21+
- Gradle
- Docker (for Postgres)

## Quick Start

1. Start Postgres:
```bash
docker compose up -d
```

2. Run the app:
```bash
./gradlew bootRun
```

The app connects to `localhost:5432` with default credentials and creates tables on startup.

## Stop Postgres
```bash
docker compose down
```

To wipe data and start fresh:
```bash
docker compose down -v
```

## Notes
- Tables are created from `src/main/resources/schema.sql` on startup.
- Default DB credentials are in `docker-compose.yml` and `application.properties`.

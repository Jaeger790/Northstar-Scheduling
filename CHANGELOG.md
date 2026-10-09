# Changelog

## 2.0.0 — Northstar redesign

- Rebuilt the UI as a unified JavaFX scheduling workspace with responsive dashboard, sidebar and editable directories.
- Added local SQLite persistence with constrained schema, indexes and foreign keys.
- Added locally-created admin accounts with salted PBKDF2 password hashing; removed prefilled login credentials and sensitive audit logging.
- Preserved the main scheduling flows: CRUD for customers and appointments, contacts, reports, local-time rendering, next-15-minute notice and weekly/monthly filtering.
- Added contact conflict detection, transactional writes and robust date/time validation.
- Added optional synthetic screenshot-data generator and test coverage.
- Removed old FXML/theme inconsistencies, hardcoded database connection and IDE metadata.
- Documented functional differences, security actions and future upgrade path.

## 2.0.0 — Maven build migration

- Replaced Gradle with Maven (pom.xml), preserving JavaFX 21.0.5, SQLite JDBC 3.46.1.3, JUnit 5.11.4 and Java 21.
- Updated CI to Maven verify and documentation to `mvn clean verify` / `mvn javafx:run`.
- Removed Gradle wrapper and Gradle build files; application source and resources unchanged.

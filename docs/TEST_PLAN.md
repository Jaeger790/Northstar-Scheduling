# Test plan

- `mvn clean verify` — runs JUnit 5 tests (password salts, authentication, DST gaps/overlaps, database restart persistence, CRUD, overlapping appointments, FK restrictions).
- `mvn javafx:run` — JavaFX smoke test: first-run admin, login, add contact, add customer, book appointment, search, filter, modify, delete, inspect reports, restart application and confirm persistence.
- SQLite structural and constraint checks may also be performed using a local SQLite interpreter.

**The user must verify** full Maven/JavaFX execution on a desktop with downloaded dependencies. A static inspection of source or standalone compilation of domain/service code is not a substitute for full GUI verification.

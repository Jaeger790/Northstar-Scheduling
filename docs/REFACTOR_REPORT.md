# Refactor report (v2.0)

## Original findings

1. **CRITICAL: Database credentials were hard-coded** (`src/main/java/crumbTracker/DAO/JDBC.java`, including a root password). Assume any real/reused credential exposed in Git history is compromised; rotate it and consider removing sensitive history before making the project public. Removing it in a new commit does **not** erase older revisions.
2. **CRITICAL: The original login audit wrote plaintext attempted passwords** to `login_activity.txt`, a file that was also committed. No passwords or credential guesses are recorded in this version. If the historical contents are sensitive, purge the file from Git history and rotate affected credentials.
3. The original app depended on an external `datacrumbs` database. There was no schema migration or reproducible local setup.
4. SQL handling and exceptions were scattered across static DAO classes and controllers. Some queries concatenated input while other operations silently logged errors, obscuring failures.
5. Navigation recreated FXML controllers and scenes, with duplicated table formatting and inconsistent UI styles.
6. Model classes mixed JavaFX properties, reporting summaries and scheduling data.
7. The original login used plain-text password comparisons. The UI prefilled an account/password pair.
8. Appointments were manually assigned IDs by scanning database records, creating collision risks.

## This refactor

- Replaced the FXML page collection with a consistent programmatic JavaFX UI and one CSS theme.
- Reorganized into immutable domain records, database infrastructure, repositories, services, and desktop UI.
- Added SQLite creation, foreign keys, prepared statements, DB-generated IDs, contact overlap checks, and restart persistence.
- Added password hashing and first-run local admin registration; no baked-in users or sensitive login logs.
- Standardized UTC-based persistence and system-local date/time display, including DST validation.
- Added tests covering password hashing, daylight-saving handling, CRUD, FK protection, overlap detection and persistence (dependency-aware tests).
- Converted the JavaFX build to Maven with Java 21 compatible versions, added CI and repository hygiene.

## Scope differences

- The old hardcoded **Eastern time office hours** constraint was not retained. This general-purpose scheduler accepts valid local date/time ranges and is not restricted to East Coast business hours. A configurable office-hours policy is recommended.
- The old login UI had German/French strings. Multilingual UI support was not ported; internationalization is a recommended next step.
- Legacy country/division ID lookups are represented as customer region/country text because the source repository did not include the SQL reference tables.
- Appointment status, role-based access, reminders, export, and multi-user remote sync are **not implemented**.
- No attempt was made to recover unavailable MySQL data.

## Suggested next upgrades

1. **Migration tool for old MySQL dump** if you locate it.
2. **Calendar week/day view** with drag-and-drop rescheduling.
3. **Office hours and time-off policies** per contact, including timezone configuration.
4. **CSV export, backup/restore, data encryption**, and a documented privacy policy.
5. **Roles, secure password reset, account lockout, and audit metadata** for any real multi-user scenario.
6. **Email/desktop reminders**, conflict visualization, recurring bookings, and custom labels.
7. **True UI integration tests** (TestFX) and installer packaging (`jpackage`).

## Verification notes

The source structure, pure-Java service layer, schema statements and packaging can be validated offline. Full JavaFX/Maven runtime testing requires Maven and downloaded dependencies. Verify with `mvn clean verify` and `mvn javafx:run` on a JDK 21 desktop before publishing a tagged release.

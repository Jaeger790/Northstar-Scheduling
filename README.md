# Northstar Scheduler

A redesigned, local-first **JavaFX desktop scheduling application**, refactored from a 2023 course project.

**Java 21 · JavaFX 21 · Maven · SQLite (JDBC) · JUnit 5**

## Features

- Professional navigation shell, overview dashboard, appointment planner, customer directory, contact management, and reporting.
- Add, edit, search and delete appointments, with today / week / month filters and a 15-minute reminder on the overview.
- Prevent overlapping appointments for the same contact; back-to-back bookings are allowed.
- Editable customer and contact directories, with database-enforced referential integrity.
- Reports by appointment type, month, location, customer region and contact workload, plus a contact schedule.
- Stores timestamps in UTC, displays times in the user's local time zone, and rejects ambiguous/nonexistent DST wall-clock times.
- SQLite database created automatically; no MySQL server or pre-existing schema required.
- First-run admin creation and password verification using salted PBKDF2-SHA256 hashes. No default credentials.

## Run

Install **JDK 21** and **Maven 3.9+**, extract this repository, and open a terminal in the `NorthstarScheduler` directory (the folder containing `pom.xml`). On Arch Linux:

```bash
sudo pacman -Syu jdk21-openjdk maven
sudo archlinux-java set java-21-openjdk
java -version
mvn -version
```

Compile, run the JUnit 5 suite, and start the JavaFX UI:

```bash
mvn clean verify
mvn javafx:run
```

You can run only the tests with `mvn test`, or package the project with `mvn package` (the generated JAR is **not** a standalone/fat JAR; use `mvn javafx:run` to launch with dependencies). Maven downloads dependencies from Maven Central on the first build, so internet access is needed for that initial download. These commands also work in Windows PowerShell after JDK 21 and Maven are installed. After first startup, create a local admin (minimum password length: 10 characters). Then add a contact, a customer, and an appointment.

> NOTE: This is a single-user/local demo with a basic password login. It is **not** a hosted, multi-user production identity/security system. Do not use it as a sensitive customer-data service without a thorough security and privacy assessment.

## Where data lives

- Linux / macOS: `~/.northstar-scheduler/schedule.db`
- Windows: `%USERPROFILE%\.northstar-scheduler\schedule.db`
- Override with environment variable `SCHEDULER_DB_PATH=/path/to/schedule.db` (an absolute path is recommended).

Make backups **while the application is closed**. SQLite may create `-wal` / `-shm` files while running; never copy just the `.db` file during writes.

## Project layout

```
src/main/java/com/northstar/scheduler/
├── Launcher.java                 # JavaFX-compatible JVM entry point
├── SchedulerApp.java             # JavaFX views and interaction
├── db/Database.java              # Schema and connections
├── data/SchedulerRepository.java # Parameterized SQL and CRUD
├── domain/                       # Immutable model records
└── service/                      # Scheduling rules and password hashes
src/main/resources/com/northstar/scheduler/theme.css
src/test/java/                  # Domain/service/repository tests
```

See `docs/REFACTOR_REPORT.md` for the original findings and remaining upgrade recommendations.

## Original data / migrations

The original app used a MySQL database named `datacrumbs`. **Its original MySQL data was not in this repository**; the new SQLite database starts empty. The new schema is not a drop-in import of that database. If you find an old SQL dump, add an explicit migration script mapping legacy customers, contacts, and appointments (including IDs and timestamp/time zone semantics). Do not directly replace the SQLite database with a MySQL dump.

## Portfolio / licensing

This rewrite intentionally avoids committing database contents, secret credentials, and login attempts. Screenshots can be added to `docs/screenshots/` after verifying the app on a desktop. Add a license only if you're certain you own the right to publish all included code.

## Optional: sample portfolio data

For screenshots, you can populate **an already-initialized, empty database** with fictional appointments. Close the application and run:

```bash
python3 tools/seed_demo.py --database ~/.northstar-scheduler/schedule.db
```

On Windows, provide the matching path under `%USERPROFILE%`. The script refuses to run if customers, contacts, or appointments already exist; it never modifies the stored admin password. All names and email addresses are fictional example data.

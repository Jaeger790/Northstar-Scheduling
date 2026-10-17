# Architecture

`JavaFX UI -> SchedulingService -> SchedulerRepository -> SQLite Database`

- `SchedulerApp`: screen rendering, state and UI events. No SQL.
- `SchedulingService`: input invariants, time conversion and validation, delegates writes.
- `SchedulerRepository`: scoped connections and prepared SQL, ordered lists, atomic overlap checks and writes.
- `Database`: local file path, connection pragmas, schema creation and database initialization.
- `domain`: immutable appointment, customer and contact records.
- `PasswordHasher`: salted PBKDF2-SHA256 hashes, constant-time comparison, no password logging.

Data stored as Unix epoch **milliseconds**. SQL comparisons operate on instants; reports convert to local time for display. Customer/contact deletion is restricted when referenced by appointments.

## Future database migration

The current design uses SQLite-specific `PRAGMA` and `BEGIN IMMEDIATE` SQL inside the database/repository layer. To support MySQL, introduce a dialect or separate repository and migration layer, rather than switching a connection URL alone.

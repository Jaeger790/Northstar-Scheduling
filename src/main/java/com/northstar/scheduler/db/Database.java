package com.northstar.scheduler.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {
    private final Path path;

    public Database(Path path) { this.path = path.toAbsolutePath().normalize(); }

    public static Path defaultPath() {
        String override = System.getenv("SCHEDULER_DB_PATH");
        if (override != null && !override.isBlank()) return Path.of(override);
        return Path.of(System.getProperty("user.home"), ".northstar-scheduler", "schedule.db");
    }

    public Path path() { return path; }

    public Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA busy_timeout = 5000");
        } catch (SQLException ex) { connection.close(); throw ex; }
        return connection;
    }

    public void initialize() throws SQLException {
        try { Files.createDirectories(path.getParent()); }
        catch (IOException e) { throw new SQLException("Cannot create database directory", e); }
        try (Connection c = connect(); Statement st = c.createStatement()) {
            try (var version = st.executeQuery("PRAGMA user_version")) {
                if (version.next() && version.getInt(1) > 1) {
                    throw new SQLException("Database schema is newer than this application supports.");
                }
            }
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("""
                CREATE TABLE IF NOT EXISTS users (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  username TEXT NOT NULL UNIQUE COLLATE NOCASE,
                  password_hash TEXT NOT NULL,
                  created_at INTEGER NOT NULL DEFAULT (unixepoch())
                )
                """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS customers (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  name TEXT NOT NULL CHECK(length(trim(name)) > 0),
                  address TEXT NOT NULL DEFAULT '', postal_code TEXT NOT NULL DEFAULT '',
                  phone TEXT NOT NULL DEFAULT '', region TEXT NOT NULL DEFAULT '',
                  country TEXT NOT NULL DEFAULT ''
                )
                """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS contacts (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  name TEXT NOT NULL CHECK(length(trim(name)) > 0),
                  email TEXT NOT NULL DEFAULT ''
                )
                """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS appointments (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  title TEXT NOT NULL CHECK(length(trim(title)) > 0),
                  description TEXT NOT NULL DEFAULT '', location TEXT NOT NULL DEFAULT '',
                  type TEXT NOT NULL CHECK(length(trim(type)) > 0),
                  start_utc INTEGER NOT NULL, end_utc INTEGER NOT NULL,
                  customer_id INTEGER NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
                  contact_id INTEGER NOT NULL REFERENCES contacts(id) ON DELETE RESTRICT,
                  CHECK(end_utc > start_utc)
                )
                """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_appointments_start ON appointments(start_utc)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_appointments_contact ON appointments(contact_id,start_utc,end_utc)");
            st.execute("PRAGMA user_version=1");
        }
    }
}

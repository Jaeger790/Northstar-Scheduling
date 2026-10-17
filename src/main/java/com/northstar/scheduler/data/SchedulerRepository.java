package com.northstar.scheduler.data;

import com.northstar.scheduler.db.Database;
import com.northstar.scheduler.domain.Appointment;
import com.northstar.scheduler.domain.Contact;
import com.northstar.scheduler.domain.Customer;
import com.northstar.scheduler.service.PasswordHasher;
import com.northstar.scheduler.service.SchedulingService;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class SchedulerRepository {
    private final Database database;
    public SchedulerRepository(Database database) { this.database = database; }

    public boolean hasUsers() throws SQLException {
        try (Connection c = database.connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT EXISTS(SELECT 1 FROM users)")) { return rs.next() && rs.getInt(1) == 1; }
    }

    public void createFirstAdmin(String username, char[] password) throws SQLException {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Username is required.");
        // Generate hash before opening the write transaction.
        String hash = PasswordHasher.hash(password);
        try (Connection c = database.connect()) {
            c.setAutoCommit(false);
            try {
                try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT EXISTS(SELECT 1 FROM users)")) {
                    if (rs.next() && rs.getInt(1) == 1) throw new IllegalStateException("An account already exists.");
                }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO users(username,password_hash) VALUES (?,?)")) {
                    ps.setString(1, username.trim()); ps.setString(2, hash); ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException | RuntimeException ex) { c.rollback(); throw ex; }
        }
    }

    public boolean authenticate(String username, char[] password) throws SQLException {
        if (username == null || username.isBlank()) return false;
        try (Connection c = database.connect(); PreparedStatement ps = c.prepareStatement("SELECT password_hash FROM users WHERE username = ?")) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && PasswordHasher.verify(password, rs.getString(1));
            }
        }
    }

    public List<Customer> customers() throws SQLException {
        List<Customer> output = new ArrayList<>();
        try (Connection c = database.connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM customers ORDER BY name COLLATE NOCASE, id")) {
            while (rs.next()) output.add(readCustomer(rs));
        }
        return output;
    }

    private Customer readCustomer(ResultSet rs) throws SQLException {
        return new Customer(rs.getLong("id"),rs.getString("name"),rs.getString("address"),
                rs.getString("postal_code"),rs.getString("phone"),rs.getString("region"),rs.getString("country"));
    }

    public long saveCustomer(Customer customer) throws SQLException {
        if (customer.name() == null || customer.name().isBlank()) throw new IllegalArgumentException("Customer name is required.");
        boolean create = customer.id() == 0;
        String sql = create ? "INSERT INTO customers(name,address,postal_code,phone,region,country) VALUES (?,?,?,?,?,?)"
                : "UPDATE customers SET name=?,address=?,postal_code=?,phone=?,region=?,country=? WHERE id=?";
        try (Connection c = database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1,customer.name().trim()); ps.setString(2,orEmpty(customer.address()));
            ps.setString(3,orEmpty(customer.postalCode())); ps.setString(4,orEmpty(customer.phone()));
            ps.setString(5,orEmpty(customer.region())); ps.setString(6,orEmpty(customer.country()));
            if (!create) ps.setLong(7,customer.id());
            if (ps.executeUpdate() != 1) throw new SQLException("Customer was not found.");
            return create ? generatedKey(c) : customer.id();
        }
    }

    public void deleteCustomer(long id) throws SQLException { delete("DELETE FROM customers WHERE id=?", id); }

    public List<Contact> contacts() throws SQLException {
        List<Contact> output = new ArrayList<>();
        try (Connection c = database.connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM contacts ORDER BY name COLLATE NOCASE, id")) {
            while (rs.next()) output.add(new Contact(rs.getLong("id"),rs.getString("name"),rs.getString("email")));
        }
        return output;
    }

    public long saveContact(Contact contact) throws SQLException {
        if (contact.name() == null || contact.name().isBlank()) throw new IllegalArgumentException("Contact name is required.");
        boolean create = contact.id() == 0;
        String sql = create ? "INSERT INTO contacts(name,email) VALUES (?,?)" : "UPDATE contacts SET name=?,email=? WHERE id=?";
        try (Connection c = database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, contact.name().trim()); ps.setString(2,orEmpty(contact.email()));
            if (!create) ps.setLong(3,contact.id());
            if (ps.executeUpdate() != 1) throw new SQLException("Contact was not found.");
            return create ? generatedKey(c) : contact.id();
        }
    }

    public void deleteContact(long id) throws SQLException { delete("DELETE FROM contacts WHERE id=?", id); }

    public List<Appointment> appointments() throws SQLException {
        List<Appointment> output = new ArrayList<>();
        try (Connection c = database.connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM appointments ORDER BY start_utc,id")) {
            while (rs.next()) output.add(readAppointment(rs));
        }
        return output;
    }

    public List<Appointment> appointmentsForContact(long contactId) throws SQLException {
        List<Appointment> output = new ArrayList<>();
        try (Connection c = database.connect(); PreparedStatement ps = c.prepareStatement("SELECT * FROM appointments WHERE contact_id=? ORDER BY start_utc")) {
            ps.setLong(1, contactId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) output.add(readAppointment(rs)); }
        }
        return output;
    }

    private Appointment readAppointment(ResultSet rs) throws SQLException {
        return new Appointment(rs.getLong("id"), rs.getString("title"), rs.getString("description"),
                rs.getString("location"),rs.getString("type"), Instant.ofEpochMilli(rs.getLong("start_utc")),
                Instant.ofEpochMilli(rs.getLong("end_utc")),rs.getLong("customer_id"),rs.getLong("contact_id"));
    }

        public boolean conflicts(long contactId, Instant start, Instant end, long excludeId) throws SQLException {
        try (Connection c = database.connect(); PreparedStatement ps = c.prepareStatement("""
                SELECT EXISTS(SELECT 1 FROM appointments
                    WHERE contact_id=? AND start_utc < ? AND end_utc > ? AND id <> ?)
                """)) {
            ps.setLong(1,contactId); ps.setLong(2,end.toEpochMilli());
            ps.setLong(3,start.toEpochMilli()); ps.setLong(4,excludeId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() && rs.getInt(1) == 1; }
        }
    }

    public long saveAppointment(Appointment apt) throws SQLException {
        SchedulingService.validate(apt);
        // Lock writers before checking for overlaps.
        try (Connection c = database.connect()) {
            try (Statement st = c.createStatement()) { st.execute("BEGIN IMMEDIATE"); }
            try {
                try (PreparedStatement check = c.prepareStatement("""
                        SELECT EXISTS(SELECT 1 FROM appointments
                         WHERE contact_id=? AND start_utc < ? AND end_utc > ? AND id <> ?)
                        """)) {
                    check.setLong(1,apt.contactId()); check.setLong(2,apt.end().toEpochMilli());
                    check.setLong(3,apt.start().toEpochMilli()); check.setLong(4,apt.id());
                    try (ResultSet rs = check.executeQuery()) {
                        if (rs.next() && rs.getInt(1) == 1)
                            throw new IllegalArgumentException("This contact already has an appointment during that time.");
                    }
                }
                boolean create = apt.id() == 0;
                String sql = create ? """
                        INSERT INTO appointments(title,description,location,type,start_utc,end_utc,customer_id,contact_id)
                        VALUES (?,?,?,?,?,?,?,?)
                        """ : """
                        UPDATE appointments SET title=?,description=?,location=?,type=?,start_utc=?,end_utc=?,customer_id=?,contact_id=?
                        WHERE id=?
                        """;
                long id;
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setString(1,apt.title().trim()); ps.setString(2,orEmpty(apt.description()));
                    ps.setString(3,orEmpty(apt.location())); ps.setString(4,apt.type().trim());
                    ps.setLong(5,apt.start().toEpochMilli()); ps.setLong(6,apt.end().toEpochMilli());
                    ps.setLong(7,apt.customerId()); ps.setLong(8,apt.contactId());
                    if (!create) ps.setLong(9,apt.id());
                    if (ps.executeUpdate() != 1) throw new SQLException("Appointment was not found.");
                    id = create ? generatedKey(c) : apt.id();
                }
                try (Statement st = c.createStatement()) { st.execute("COMMIT"); }
                return id;
            } catch (SQLException | RuntimeException e) {
                try (Statement st = c.createStatement()) { st.execute("ROLLBACK"); }
                catch (SQLException rollbackFailure) { e.addSuppressed(rollbackFailure); }
                throw e;
            }
        }
    }

    public void deleteAppointment(long id) throws SQLException { delete("DELETE FROM appointments WHERE id=?",id); }

    private void delete(String sql, long id) throws SQLException {
        try (Connection c = database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1,id);
            if (ps.executeUpdate() != 1) throw new SQLException("Record not found.");
        }
    }

    private long generatedKey(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
            if (rs.next()) return rs.getLong(1);
        }
        throw new SQLException("Unable to retrieve new record ID.");
    }

    private String orEmpty(String s) { return s == null ? "" : s.trim(); }
}

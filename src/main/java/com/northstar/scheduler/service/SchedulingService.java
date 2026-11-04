package com.northstar.scheduler.service;

import com.northstar.scheduler.data.SchedulerRepository;
import com.northstar.scheduler.domain.Appointment;
import java.sql.SQLException;
import java.time.*;
import java.util.List;

public final class SchedulingService {
    private final SchedulerRepository repository;
    public SchedulingService(SchedulerRepository repository) { this.repository = repository; }

    public long save(Appointment appointment) throws SQLException {
        validate(appointment);
        return repository.saveAppointment(appointment);
    }

    public static void validate(Appointment appointment) {
        if (appointment == null) throw new IllegalArgumentException("Appointment is required.");
        if (appointment.id() < 0) throw new IllegalArgumentException("Appointment ID cannot be negative.");
        if (appointment.title() == null || appointment.title().isBlank())
            throw new IllegalArgumentException("Enter an appointment title.");
        if (appointment.type() == null || appointment.type().isBlank())
            throw new IllegalArgumentException("Select an appointment type.");
        if (appointment.start() == null || appointment.end() == null || !appointment.end().isAfter(appointment.start()))
            throw new IllegalArgumentException("End time must be after start time.");
        if (Duration.between(appointment.start(),appointment.end()).compareTo(Duration.ofDays(7)) > 0)
            throw new IllegalArgumentException("An appointment cannot run longer than seven days.");
        if (appointment.customerId() <= 0 || appointment.contactId() <= 0)
            throw new IllegalArgumentException("Select a customer and contact.");
    }

    public static Instant fromLocal(LocalDate date, LocalTime time, ZoneId zone) {
        if (date == null || time == null || zone == null) throw new IllegalArgumentException("Date, time and time zone are required.");
        LocalDateTime local = LocalDateTime.of(date,time);
        List<ZoneOffset> valid = zone.getRules().getValidOffsets(local);
        if (valid.isEmpty()) throw new IllegalArgumentException("That local time does not exist due to daylight saving time.");
        if (valid.size() > 1) throw new IllegalArgumentException("That local time is ambiguous due to daylight saving time.");
        return local.atOffset(valid.get(0)).toInstant();
    }
}

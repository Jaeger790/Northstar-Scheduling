package com.northstar.scheduler.domain;

import java.time.Instant;

/** Store UTC instants; convert to local time in the UI. */
public record Appointment(long id, String title, String description, String location,
                          String type, Instant start, Instant end, long customerId, long contactId) { }

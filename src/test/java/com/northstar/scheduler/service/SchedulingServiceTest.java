package com.northstar.scheduler.service;

import com.northstar.scheduler.domain.Appointment;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class SchedulingServiceTest {
    @Test void rejectsNegativeDuration() {
        Instant now=Instant.parse("2026-10-01T12:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> SchedulingService.validate(
                new Appointment(0,"Call","","","Consultation",now,now.minusSeconds(1),1,1)));
    }
    @Test void acceptsBackToBackCompatibleTimes() {
        Instant now=Instant.parse("2026-10-01T12:00:00Z");
        assertDoesNotThrow(()->SchedulingService.validate(new Appointment(0,"Call","","","Consultation",now,now.plusSeconds(1800),1,1)));
    }
    @Test void rejectsAnAppointmentLongerThanSevenDays() {
        Instant now=Instant.parse("2026-10-01T12:00:00Z");
        assertThrows(IllegalArgumentException.class,()->SchedulingService.validate(new Appointment(
                0,"Overlong","","","Review",now,now.plus(Duration.ofDays(7)).plusSeconds(1),1,1)));
    }
    @Test void rejectsSpringForwardGap() {
        ZoneId zone=ZoneId.of("America/New_York");
        assertThrows(IllegalArgumentException.class,()->SchedulingService.fromLocal(LocalDate.of(2026,3,8),LocalTime.of(2,30),zone));
    }
    @Test void rejectsFallBackAmbiguity() {
        ZoneId zone=ZoneId.of("America/New_York");
        assertThrows(IllegalArgumentException.class,()->SchedulingService.fromLocal(LocalDate.of(2026,11,1),LocalTime.of(1,30),zone));
    }
    @Test void convertsValidLocalTimeToInstant() {
        assertEquals(Instant.parse("2026-01-15T17:00:00Z"),SchedulingService.fromLocal(
                LocalDate.of(2026,1,15),LocalTime.of(9,0),ZoneId.of("America/Los_Angeles")));
    }
}

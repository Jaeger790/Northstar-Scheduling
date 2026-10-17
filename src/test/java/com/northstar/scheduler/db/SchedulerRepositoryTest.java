package com.northstar.scheduler.db;

import com.northstar.scheduler.data.SchedulerRepository;
import com.northstar.scheduler.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class SchedulerRepositoryTest {
    @TempDir Path tempDir;
    SchedulerRepository repository() throws SQLException {
        Database db=new Database(tempDir.resolve("records.db"));
        db.initialize();return new SchedulerRepository(db);
    }
    @Test void recordsSurviveNewRepositoryInstance() throws Exception {
        SchedulerRepository r=repository();
        long cid=r.saveCustomer(new Customer(0,"Aster Co.","12 Main St","12345","555-0100","CA","US"));
        long kid=r.saveContact(new Contact(0,"Jordan","jordan@example.com"));
        long aid=r.saveAppointment(new Appointment(0,"Discovery","Demo","Online","Consultation",
                Instant.parse("2026-10-08T15:00:00Z"),Instant.parse("2026-10-08T16:00:00Z"),cid,kid));
        assertTrue(aid>0);
        SchedulerRepository reopened=repository();
        assertEquals(1,reopened.customers().size());
        assertEquals(1,reopened.contacts().size());
        assertEquals(1,reopened.appointments().size());
        assertEquals("Discovery",reopened.appointments().get(0).title());
    }
    @Test void preventsOverlappingContactBookingsButAllowsAdjacentAndOtherContact() throws Exception {
        SchedulerRepository r=repository();
        long c=r.saveCustomer(new Customer(0,"A","","","","",""));
        long a=r.saveContact(new Contact(0,"Alice",""));
        long b=r.saveContact(new Contact(0,"Bob",""));
        Instant start=Instant.parse("2026-10-08T15:00:00Z");
        r.saveAppointment(new Appointment(0,"First","","","Consultation",start,start.plusSeconds(3600),c,a));
        assertThrows(IllegalArgumentException.class,()->r.saveAppointment(new Appointment(0,"Overlap","","","Consultation",
                start.plusSeconds(60),start.plusSeconds(3700),c,a)));
        assertDoesNotThrow(()->r.saveAppointment(new Appointment(0,"Adjacent","","","Consultation",
                start.plusSeconds(3600),start.plusSeconds(5400),c,a)));
        assertDoesNotThrow(()->r.saveAppointment(new Appointment(0,"Other contact","","","Consultation",
                start,start.plusSeconds(3600),c,b)));
    }
    @Test void foreignKeysProtectInUseCustomersAndContacts() throws Exception {
        SchedulerRepository r=repository();
        long c=r.saveCustomer(new Customer(0,"Customer","","","","",""));
        long k=r.saveContact(new Contact(0,"Contact",""));
        r.saveAppointment(new Appointment(0,"Meeting","","","Review",Instant.EPOCH,Instant.EPOCH.plusSeconds(60),c,k));
        assertThrows(SQLException.class,()->r.deleteCustomer(c));
        assertThrows(SQLException.class,()->r.deleteContact(k));
    }
    @Test void storesPasswordHashesAndChecksAuthentication() throws Exception {
        SchedulerRepository r=repository();
        assertFalse(r.hasUsers());
        r.createFirstAdmin("manager","a good long password".toCharArray());
        assertTrue(r.hasUsers());
        assertTrue(repository().authenticate("manager","a good long password".toCharArray()));
        assertFalse(repository().authenticate("manager","invalid".toCharArray()));
        assertThrows(IllegalStateException.class,()->r.createFirstAdmin("other","a good long password".toCharArray()));
    }
    @Test void repositoryRejectsAppointmentsLongerThanSevenDays() throws Exception {
        SchedulerRepository r = repository();
        long customer = r.saveCustomer(new Customer(0, "Customer", "", "", "", "", ""));
        long contact = r.saveContact(new Contact(0, "Contact", ""));
        Instant start = Instant.parse("2026-10-08T15:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> r.saveAppointment(new Appointment(
                0, "Long meeting", "", "", "Review", start, start.plusSeconds(604801), customer, contact)));
        assertTrue(r.appointments().isEmpty());
    }

    @Test void refusesNewerSchemaWithoutChangingVersion() throws Exception {
        Database db = new Database(tempDir.resolve("future.db"));
        db.initialize();
        try (var c = db.connect(); var st = c.createStatement()) {
            st.execute("PRAGMA user_version=2");
        }
        assertThrows(SQLException.class, db::initialize);
        try (var c = db.connect(); var st = c.createStatement();
             var result = st.executeQuery("PRAGMA user_version")) {
            assertTrue(result.next());
            assertEquals(2, result.getInt(1));
        }
    }
}

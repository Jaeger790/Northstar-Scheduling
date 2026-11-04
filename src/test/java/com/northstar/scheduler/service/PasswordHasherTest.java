package com.northstar.scheduler.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {
    @Test void producesSaltedHashesAndVerifiesCorrectly() {
        String first=PasswordHasher.hash("correct horse battery".toCharArray());
        String second=PasswordHasher.hash("correct horse battery".toCharArray());
        assertNotEquals(first,second);
        assertTrue(PasswordHasher.verify("correct horse battery".toCharArray(),first));
        assertFalse(PasswordHasher.verify("wrong pass".toCharArray(),first));
        assertFalse(PasswordHasher.verify("correct horse battery".toCharArray(),"invalid"));
    }
    @Test void rejectsShortPasswords() {
        assertThrows(IllegalArgumentException.class,()->PasswordHasher.hash("short".toCharArray()));
    }
}

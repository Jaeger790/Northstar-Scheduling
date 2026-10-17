package com.northstar.scheduler.service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class PasswordHasher {
    private static final int ITERATIONS = 210_000;
    private static final int KEY_BITS = 256;
    private PasswordHasher() { }

    public static String hash(char[] password) {
        if (password == null || password.length < 10) throw new IllegalArgumentException("Use at least 10 characters.");
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] digest = pbkdf(password, salt, ITERATIONS);
        return "pbkdf2-sha256$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(digest);
    }

    public static boolean verify(char[] password, String encoded) {
        if (password == null || encoded == null) return false;
        try {
            String[] parts = encoded.split("\\$");
            if (parts.length != 4 || !parts[0].equals("pbkdf2-sha256")) return false;
            int rounds = Integer.parseInt(parts[1]);
            if (rounds < 100_000 || rounds > 2_000_000) return false;
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            if (salt.length != 16 || expected.length != KEY_BITS / 8) return false;
            return MessageDigest.isEqual(expected, pbkdf(password, salt, rounds));
        } catch (RuntimeException e) { return false; }
    }

    private static byte[] pbkdf(char[] password, byte[] salt, int rounds) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, rounds, KEY_BITS);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (Exception e) { throw new IllegalStateException("Password hashing is unavailable", e); }
        finally { spec.clearPassword(); }
    }
}

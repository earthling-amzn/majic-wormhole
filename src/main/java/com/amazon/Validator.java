package com.amazon;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class Validator {
    private final MessageDigest digester;
    private byte[] expected;

    public Validator() {
        try {
            this.digester = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public void expect(byte[] expected) {
        if (expected == null) {
            throw new IllegalArgumentException("Expected checksum is required.");
        }
        this.expected = expected;
    }

    public void update(byte[] data, int offset, int length) {
        digester.update(data, offset, length);
    }

    public void update(ByteBuffer buffer) {
        digester.update(buffer);
    }

    public void validate() {
        byte[] digest = digester.digest();
        if (Arrays.equals(digest, expected)) {
            System.out.println("Checksums match.");
        } else {
            System.err.printf("Checksum mismatch: Expected: %s, Received: %s\n",
                    Wormhole.toHex(expected), Wormhole.toHex(digest));
            throw new IllegalStateException("Invalid digest.");
        }
    }
}

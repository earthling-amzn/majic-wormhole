package com.amazon;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class HeaderTest {
    @Test
    public void testMarshalHeader() {
        var header = new Header("Sender", "File.txt", 20, new byte[] {0xC, 0xA, 0xF, 0xE});
        var encoded = header.encode();
        var decoded = Header.decode(encoded);
        assertEquals(header.sender(), decoded.sender());
        assertEquals(header.filePath(), decoded.filePath());
        assertEquals(header.fileLength(), decoded.fileLength());
        assertArrayEquals(header.checksum(), decoded.checksum());
    }

    @Test
    public void testMarshalHeaderWithoutChecksum() {
        var header = new Header("Sender", "File.txt", 20, null);
        var encoded = header.encode();
        var decoded = Header.decode(encoded);
        assertEquals(header.sender(), decoded.sender());
        assertEquals(header.filePath(), decoded.filePath());
        assertEquals(header.fileLength(), decoded.fileLength());
        assertArrayEquals(header.checksum(), decoded.checksum());
    }

    @Test
    public void testPaths() {
        Path path = Paths.get("/root/one/two");
        System.out.println(path.subpath(1, path.getNameCount()));
    }
}

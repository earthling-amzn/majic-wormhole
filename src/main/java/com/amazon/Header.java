package com.amazon;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public record Header(String sender, String filePath, long fileLength, byte[] checksum) {

    byte[] encode() {
        var checksumLength = checksum == null || checksum.length == 0 ? 0 : checksum.length;
        var totalLength = Short.BYTES + sender.length() + Short.BYTES + filePath.length() + Long.BYTES + Short.BYTES + checksumLength;
        var buffer = ByteBuffer.allocate(totalLength);
        byte[] bytes = sender.getBytes(StandardCharsets.UTF_8);
        buffer.putShort((short) bytes.length);
        buffer.put(bytes);
        bytes = filePath.getBytes(StandardCharsets.UTF_8);
        buffer.putShort((short) bytes.length);
        buffer.put(bytes);
        buffer.putLong(fileLength);
        if (checksumLength == 0) {
            buffer.putShort((short) 0);
        } else {
            buffer.putShort((short) checksum.length);
            buffer.put(checksum);
        }
        return buffer.array();
    }

    static Header decode(byte[] encoded) {
        var buffer = ByteBuffer.wrap(encoded);

        var senderLength = buffer.getShort();
        var sender = new String(encoded, buffer.position(), senderLength, StandardCharsets.UTF_8);
        buffer.position(buffer.position() + senderLength);

        var filePathLength = buffer.getShort();
        var filePath = new String(encoded, buffer.position(), filePathLength, StandardCharsets.UTF_8);
        buffer.position(buffer.position() + filePathLength);

        var fileLength = buffer.getLong();

        var checksumLength = buffer.getShort();
        byte [] checksum = null;
        if (checksumLength != 0) {
            checksum = new byte[checksumLength];
            buffer.get(checksum, 0, checksumLength);
        }

        return new Header(sender, filePath, fileLength, checksum);
    }
}

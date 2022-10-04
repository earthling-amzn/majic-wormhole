package com.amazon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class SimpleBlockingSender implements Sender {
    private static final Logger logger = LoggerFactory.getLogger(SimpleBlockingSender.class);
    private final String senderName;
    private final int chunkSize;

    public SimpleBlockingSender(String sender, int chunkSize) {
        this.senderName = sender;
        this.chunkSize = chunkSize;
    }

    @Override
    public void send(File source, String host, int port) {
        try (Socket s = new Socket(host, port);
             FileInputStream fin = new FileInputStream(source)) {

            // Encode header as string
            String header = senderName + ":" + source.length() + ":" + source.getName() +"\n";
            logger.info("Sending upload request: {}", header);
            s.getOutputStream().write(header.getBytes(StandardCharsets.UTF_8));

            // Wait for response for receiver to proceed.
            int proceed = s.getInputStream().read();
            if (proceed == 0) {
                logger.warn("Receiver rejected uploaded.");
                return;
            }
            if (proceed == -1) {
                logger.error("Read timed out");
                return;
            }

            int transferred = 0;
            int read;
            byte[] chunk = new byte[chunkSize];
            while ((read = fin.read(chunk)) != -1) {
                s.getOutputStream().write(chunk, 0, read);
                transferred += read;
            }
            logger.info("Sent: {}", transferred);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

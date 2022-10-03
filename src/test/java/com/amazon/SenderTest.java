package com.amazon;

import org.junit.jupiter.api.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class SenderTest {

    private static Thread receiverThread;

    public void setupReceiver(Path targetDirectory, SimpleBlockingReceiver.Acceptor acceptor) throws InterruptedException {
        receiverThread = new Thread(() -> {
            var receiver = new ChannelReceiver(9000);
            receiver.setAcceptor(acceptor);
            receiver.setTargetDirectory(targetDirectory);
            receiver.receive();
        });
        receiverThread.setDaemon(true);
        receiverThread.start();
        Thread.sleep(2000);
    }

    @AfterEach
    public void teardownReceiver() throws InterruptedException {
        receiverThread.join();
    }

    @Test
    public void testSendToReceiver() throws Exception {
        var targetDirectory = Files.createTempDirectory("transfer-test");
        setupReceiver(targetDirectory, (username, filename, length) -> true);

        File transferFile = createTransferFile("Don't get too close!");
        var sender = new ChannelSender("sender");
        sender.send(transferFile, "127.0.0.1", 9000);
        assertTrue(Files.isRegularFile(targetDirectory.resolve(transferFile.getName())));
    }

    @Test
    public void testRejectedByReceiver() throws Exception {
        var targetDirectory = Files.createTempDirectory("transfer-test");
        setupReceiver(targetDirectory, (username, filename, length) -> false);

        File transferFile = createTransferFile("To my fantasy!");
        var sender = new SimpleBlockingSender("sender");
        sender.send(transferFile, "127.0.0.1", 9000);
        assertTrue(isDirectoryEmpty(targetDirectory));
    }

    private boolean isDirectoryEmpty(Path targetDirectory) throws IOException {
        try (Stream<Path> files = Files.list(targetDirectory)) {
            return files.toList().isEmpty();
        }
    }

    private File createTransferFile(String contents) {
        try {
            File f = File.createTempFile("test", "test");
            f.deleteOnExit();
            try (FileWriter writer = new FileWriter(f)) {
                writer.write(contents);
            }
            return f;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

package com.amazon;

import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
public class SenderTest {

    @Inject
    @Client("/")
    HttpClient client;

    private static Thread receiverThread;

    public void setupReceiver(Path targetDirectory, Receiver.Acceptor acceptor) {
        receiverThread = new Thread(() -> {
            var receiver = new Receiver(9000);
            receiver.setAcceptor(acceptor);
            receiver.setTargetDirectory(targetDirectory);
            receiver.handleFileUpload();
        });
        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    @AfterEach
    public void teardownReceiver() {
        try {
            receiverThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testSendToReceiver() throws HttpClientResponseException, IOException {
        var targetDirectory = Files.createTempDirectory("transfer-test");
        setupReceiver(targetDirectory, (username, filename, length) -> true);

        File transferFile = createTransferFile("Don't get too close!");
        var sender = new SimpleBlockingSender("sender");
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

package com.amazon;

import org.junit.jupiter.api.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class SenderTest {

    private Thread receiverThread;
    private Receiver receiver;

    public void setupChannelReceiver(final Receiver receiver) throws InterruptedException {
        this.receiver = receiver;
        receiverThread = new Thread(receiver::receive);
        receiverThread.setDaemon(true);
        receiverThread.start();
        receiverThread.setName("Receiver");
        Thread.sleep(100);
    }

    public void teardownReceiver() throws InterruptedException {
        receiver.stop();
        receiverThread.interrupt();
        receiverThread.join();
    }

    @Test
    public void testAcceptedBySimpleReceiver() throws Exception {
        var receiver = new SimpleBlockingReceiver(9000, Wormhole.DEFAULT_CHUNK_SIZE, true);
        var sender = new SimpleBlockingSender("sender", Wormhole.DEFAULT_CHUNK_SIZE, true);

        testAcceptedByReceiver(receiver, sender);
    }

    @Test
    public void testAcceptedByChannelReceiver() throws Exception {
        var receiver = new ChannelReceiver(9000, Wormhole.DEFAULT_CHUNK_SIZE, true);
        var sender = new ChannelSender("sender", Wormhole.DEFAULT_CHUNK_SIZE, true);

        testAcceptedByReceiver(receiver, sender);
    }

    private void testAcceptedByReceiver(Receiver receiver, Sender sender) throws Exception {
        var targetDirectory = Files.createTempDirectory("transfer-test");
        File transferFile = createTransferFile();

        receiver.setAcceptor((username, filename, length) -> true);
        receiver.setTargetDirectory(targetDirectory);
        setupChannelReceiver(receiver);

        try {
            sender.send(transferFile, "127.0.0.1", 9000);
        } finally {
            teardownReceiver();
        }

        assertTrue(Files.isRegularFile(targetDirectory.resolve(transferFile.getName())));
    }

    @Test
    public void testRejectedByChannelReceiver() throws Exception {
        var receiver = new ChannelReceiver(9000, Wormhole.DEFAULT_CHUNK_SIZE, true);
        var sender = new ChannelSender("sender", Wormhole.DEFAULT_CHUNK_SIZE, true);

        testRejectedByReceiver(receiver, sender);
    }

    @Test
    public void testRejectedBySimpleReceiver() throws Exception {
        var receiver = new SimpleBlockingReceiver(9000, Wormhole.DEFAULT_CHUNK_SIZE, true);
        var sender = new SimpleBlockingSender("sender", Wormhole.DEFAULT_CHUNK_SIZE, true);

        testRejectedByReceiver(receiver, sender);
    }

    private void testRejectedByReceiver(Receiver receiver, Sender sender) throws Exception {
        var targetDirectory = Files.createTempDirectory("transfer-test");
        var transferFile = createTransferFile();

        receiver.setAcceptor((username, filename, length) -> false);
        receiver.setTargetDirectory(targetDirectory);
        setupChannelReceiver(receiver);

        try {
            sender.send(transferFile, "127.0.0.1", 9000);
        } finally {
            teardownReceiver();
        }

        assertTrue(isDirectoryEmpty(targetDirectory));
    }

    @Test
    public void testSendDirectoryWithSimpleReceiver() throws Exception {
        var receiver = new SimpleBlockingReceiver();
        var sender = new SimpleBlockingSender("sender");
        testSendDirectory(receiver, sender);
    }

    @Test
    public void testSendDirectoryWithChannelReceiver() throws Exception {
        var receiver = new ChannelReceiver();
        var sender = new ChannelSender("sender");
        testSendDirectory(receiver, sender);
    }

    private void testSendDirectory(Receiver receiver, Sender sender) throws Exception {
        var targetDirectory = Files.createTempDirectory("directory-test");
        var sourceDirectory = populateSourceDirectory(5);

        receiver.setAcceptor((username, filename, length) -> true);
        receiver.setTargetDirectory(targetDirectory);
        setupChannelReceiver(receiver);

        try {
            sender.send(sourceDirectory.toFile(), "127.0.0.1", 9000);
        } finally {
            teardownReceiver();
        }
        assertEquals(5, getFileCount(targetDirectory));
    }

    private static int getFileCount(Path targetDirectory) {
        File[] files = targetDirectory.toFile().listFiles();
        return files != null ? files.length : 0;
    }

    private Path populateSourceDirectory(int fileCount) throws IOException {
        Path sourceDirectory = Files.createTempDirectory("source");
        for (int i = 0; i < fileCount; ++i) {
            Path f = Files.createTempFile(sourceDirectory, "source", "source");
            try (var writer = new FileWriter(f.toFile())) {
                writer.write("This is file: " + i);
            }
        }
        return sourceDirectory;
    }

    private boolean isDirectoryEmpty(Path targetDirectory) throws IOException {
        try (Stream<Path> files = Files.list(targetDirectory)) {
            return files.toList().isEmpty();
        }
    }

    private File createTransferFile() {
        try {
            File f = File.createTempFile("test", "test");
            f.deleteOnExit();
            try (FileWriter writer = new FileWriter(f)) {
                writer.write("Uninteresting text content");
            }
            return f;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

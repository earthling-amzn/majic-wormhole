package com.amazon;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.stream.Stream;

import static com.amazon.Wormhole.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        var receiver = new SimpleBlockingReceiver();
        var sender = new SimpleBlockingSender("sender");

        testAcceptedByReceiver(receiver, sender);
    }

    @Test
    public void testAcceptedByChannelReceiver() throws Exception {
        var receiver = new ChannelReceiver();
        var sender = new ChannelSender("sender");

        testAcceptedByReceiver(receiver, sender);
    }

    private void testAcceptedByReceiver(Receiver receiver, Sender sender) throws Exception {
        var targetDirectory = Files.createTempDirectory("transfer-test");
        File transferFile = createTransferFile();

        receiver.setAcceptor((username, filename, length) -> true);
        receiver.setTargetDirectory(targetDirectory);
        setupChannelReceiver(receiver);

        try {
            sender.send(transferFile, "127.0.0.1", DEFAULT_RECEIVER_PORT);
        } finally {
            teardownReceiver();
        }

        assertTrue(Files.isRegularFile(targetDirectory.resolve(transferFile.getName())));
    }

    @Test
    public void testRejectedByChannelReceiver() throws Exception {
        var receiver = new ChannelReceiver();
        var sender = new ChannelSender("sender");

        testRejectedByReceiver(receiver, sender);
    }

    @Test
    public void testRejectedBySimpleReceiver() throws Exception {
        var receiver = new SimpleBlockingReceiver();
        var sender = new SimpleBlockingSender("sender");

        testRejectedByReceiver(receiver, sender);
    }

    private void testRejectedByReceiver(Receiver receiver, Sender sender) throws Exception {
        var targetDirectory = Files.createTempDirectory("transfer-test");
        var transferFile = createTransferFile();

        receiver.setAcceptor((username, filename, length) -> false);
        receiver.setTargetDirectory(targetDirectory);
        setupChannelReceiver(receiver);

        try {
            sender.send(transferFile, "127.0.0.1", DEFAULT_RECEIVER_PORT);
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
    public void testSendDirectoryWithSingleThreadedSimpleReceiver() throws Exception {
        var receiver = new SimpleBlockingReceiver(DEFAULT_RECEIVER_PORT, DEFAULT_CHUNK_SIZE, 1, true);
        var sender = new SimpleBlockingSender("sender");
        testSendDirectory(receiver, sender);
    }

    @Test
    public void testSendDirectoryWithSingleThreadedSimpleSender() throws Exception {
        var receiver = new SimpleBlockingReceiver();
        var sender = new SimpleBlockingSender("sender", DEFAULT_CHUNK_SIZE, 1, true);
        testSendDirectory(receiver, sender);
    }

    @Test
    public void testSendDirectoryWithChannelReceiver() throws Exception {
        var receiver = new ChannelReceiver();
        var sender = new ChannelSender("sender");
        testSendDirectory(receiver, sender);
    }

    @Test
    public void testSendDirectoryWithSingleThreadedChannelReceiver() throws Exception {
        var receiver = new ChannelReceiver(DEFAULT_RECEIVER_PORT, DEFAULT_CHUNK_SIZE, 1, true);
        var sender = new ChannelSender("sender");
        testSendDirectory(receiver, sender);
    }

    @Test
    public void testSendDirectoryWithSingleThreadedChannelSender() throws Exception {
        var receiver = new ChannelReceiver();
        var sender = new ChannelSender("sender", DEFAULT_CHUNK_SIZE, DEFAULT_THREAD_COUNT, true);
        testSendDirectory(receiver, sender);
    }

    private void testSendDirectory(Receiver receiver, Sender sender) throws Exception {
        final var fileCount = 5;
        var targetDirectory = Files.createTempDirectory("directory-test");
        var sourceDirectory = populateSourceDirectory(fileCount);

        receiver.setAcceptor((username, filename, length) -> true);
        receiver.setTargetDirectory(targetDirectory);
        setupChannelReceiver(receiver);

        try {
            sender.send(sourceDirectory.toFile(), "127.0.0.1", DEFAULT_RECEIVER_PORT);
        } finally {
            teardownReceiver();
        }
        assertEquals(fileCount, getFileCount(targetDirectory));
    }

    private static long getFileCount(Path targetDirectory) {
        class FileCounter extends SimpleFileVisitor<Path> {
            long count = 0;
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (Files.isRegularFile(file)) {
                    ++count;
                }
                return FileVisitResult.CONTINUE;
            }
        }

        try {
            var counter = new FileCounter();
            Files.walkFileTree(targetDirectory, counter);
            return counter.count;
        } catch (IOException e) {
            return 0;
        }
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

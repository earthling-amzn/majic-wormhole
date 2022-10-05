package com.amazon;

import com.amazon.Wormhole.NamingThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.amazon.Wormhole.*;

public class ChannelReceiver implements Receiver {
    private static final Logger logger = LoggerFactory.getLogger(ChannelReceiver.class);
    private final int port;
    private final int chunkSize;
    private final boolean validate;
    private final int threadCount;

    private final ThreadLocal<ByteBuffer> buffers;
    private Path targetDirectory;

    private SimpleBlockingReceiver.Acceptor acceptor;

    private volatile boolean shouldRun = true;

    public ChannelReceiver(int port, int chunkSize, int threadCount, boolean validate) {
        this.port = port;
        this.chunkSize = chunkSize;
        this.validate = validate;
        this.threadCount = threadCount;
        this.buffers = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(chunkSize));
    }

    public ChannelReceiver() {
        this(DEFAULT_RECEIVER_PORT, DEFAULT_CHUNK_SIZE, DEFAULT_THREAD_COUNT, true);
    }

    @Override
    public void stop() {
        shouldRun = false;
    }

    @Override
    public void receive() {
        var executor = Executors.newFixedThreadPool(threadCount, new NamingThreadFactory("rx"));
        try (ServerSocketChannel ss = ServerSocketChannel.open()) {
            ss.bind(new InetSocketAddress(port));
            while (shouldRun) {
                try {
                    var clientSocket = ss.accept();
                    executor.submit(() -> receiveFiles(clientSocket));
                } catch (ClosedByInterruptException ignore) {
                    //noinspection ResultOfMethodCallIgnored
                    Thread.interrupted();
                    if (shouldRun) {
                        logger.warn("Expected shouldRun to be false when interrupted.");
                        break;
                    }
                }
            }
            logger.info("Receiver is exiting.");
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            executor.shutdown();
            boolean terminated = false;
            do {
                logger.info("Waiting for upload threads to finish.");
                try {
                    terminated = executor.awaitTermination(10, TimeUnit.SECONDS);
                    logger.debug("Terminated: {}", terminated);
                } catch (InterruptedException e) {
                    logger.debug("Interrupted: {}", Thread.currentThread().isInterrupted());
                }
            } while (!terminated);
        }
    }

    private void receiveFiles(SocketChannel clientSocket) {
        boolean received;
        do {
            received = receiveFile(clientSocket);
        } while (clientSocket.isConnected() && received);
    }

    private boolean receiveFile(SocketChannel clientSocket) {
        try {
            var validator = validate ? new Validator() : null;
            var buffer = buffers.get();

            buffer.clear();
            int read = clientSocket.read(buffer);
            if (read <= 0) {
                logger.debug("Did not read header: {}", read);
                return false;
            }

            buffer.flip();
            var header = Header.decode(buffer);
            if (validator != null) {
                validator.expect(header.checksum());
            }
            assert buffer.remaining() == 0: "Buffer should be empty here";

            if (!acceptor.accept(header.sender(), header.filePath(), header.fileLength())) {
                clientSocket.write(ByteBuffer.wrap(new byte[] {0}));
            } else {
                clientSocket.write(ByteBuffer.wrap(new byte[] {1}));
                Path withoutRoot = Wormhole.removeRoot(header.filePath());
                var filePath = targetDirectory.resolve(withoutRoot);
                Files.createDirectories(filePath.getParent());

                long writeTo = 0;
                long remaining = header.fileLength();
                logger.debug("Creating file at: <{}>", filePath);
                try (var fileOutputStream = new FileOutputStream(filePath.toFile());
                     var fileChannel = fileOutputStream.getChannel()) {

                    while (true) {
                        long toRead = Math.min(remaining, chunkSize);
                        buffer.clear();
                        buffer.limit((int) toRead);
                        read = clientSocket.read(buffer);
                        if (read <= 0) {
                            break;
                        }
                        buffer.flip();
                        int wrote = fileChannel.write(buffer, writeTo);
                        if (wrote != read) {
                            break;
                        }
                        if (validator != null) {
                            buffer.flip();
                            validator.update(buffer);
                        }
                        remaining -= wrote;
                        writeTo += wrote;
                        if (remaining == 0) {
                            break;
                        }
                    }
                }

                if (validator != null) {
                    validator.validate();
                }
                logger.debug("{} Received: {}, size: {}", clientSocket, filePath, writeTo);
            }
        } catch (Exception e) {
            logger.warn("Error receiving file.", e);
            throw new RuntimeException(e);
        }
        return true;
    }

    @Override
    public void setTargetDirectory(Path targetDirectory) {
        this.targetDirectory = targetDirectory;
    }

    @Override
    public void setAcceptor(SimpleBlockingReceiver.Acceptor acceptor) {
        this.acceptor = acceptor;
    }
}

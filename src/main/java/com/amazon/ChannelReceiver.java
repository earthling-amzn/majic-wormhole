package com.amazon;

import com.amazon.Wormhole.NamingThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.amazon.Wormhole.DEFAULT_CHUNK_SIZE;

public class ChannelReceiver implements Receiver {
    private static final Logger logger = LoggerFactory.getLogger(ChannelReceiver.class);
    private final int port;
    private final int chunkSize;
    private final boolean validate;

    private final ThreadLocal<ByteBuffer> buffers;
    private Path targetDirectory;

    private SimpleBlockingReceiver.Acceptor acceptor;

    private volatile boolean shouldRun = true;

    public ChannelReceiver(int port, int chunkSize, boolean validate) {
        this.port = port;
        this.chunkSize = chunkSize;
        this.validate = validate;
        this.buffers = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(chunkSize));
    }

    public ChannelReceiver() {
        this(9000, DEFAULT_CHUNK_SIZE, true);
    }

    @Override
    public void stop() {
        shouldRun = false;
    }

    @Override
    public void receive() {
        var executor = Executors.newFixedThreadPool(8, new NamingThreadFactory("rx"));
        try (ServerSocketChannel ss = ServerSocketChannel.open()) {
            ss.bind(new InetSocketAddress(port));
            ss.configureBlocking(false);
            while (shouldRun) {
                try {
                    var clientSocket = ss.accept();
                    executor.submit(() -> receiveFile(clientSocket));
                } catch (ClosedByInterruptException ignore) {
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
                    logger.info("Terminated: {}", terminated);
                } catch (InterruptedException e) {
                    logger.info("Interrupted: {}", Thread.currentThread().isInterrupted());
                }
            } while (!terminated);
        }
    }

    private void receiveFile(SocketChannel clientSocket) {
        try {
            var validator = validate ? new Validator() : null;
            var headers = new byte[1024];
            var headerBuffer = ByteBuffer.wrap(headers);
            int read = clientSocket.read(headerBuffer);
            if (read <= 0) {
                System.out.println("Did not read header");
                clientSocket.write(ByteBuffer.wrap(new byte[] {0}));
                return;
            }

            var header = Header.decode(headers);
            if (validator != null) {
                validator.expect(header.checksum());
            }

            if (!acceptor.accept(header.sender(), header.filePath(), header.fileLength())) {
                clientSocket.write(ByteBuffer.wrap(new byte[] {0}));
            } else {
                clientSocket.write(ByteBuffer.wrap(new byte[] {1}));
                var filePath = targetDirectory.resolve(header.filePath());
                long writeTo = 0;
                var buffer = this.buffers.get();
                buffer.clear();
                logger.info("Creating file at: <{}>", filePath);
                try (var fileOutputStream = new FileOutputStream(filePath.toFile());
                     var fileChannel = fileOutputStream.getChannel()) {

                    while (true) {
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

                        writeTo += wrote;
                        buffer.clear();
                    }
                }

                if (validator != null) {
                    validator.validate();
                }
                logger.info("{} Received: {}, size: {}", clientSocket, filePath, writeTo);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

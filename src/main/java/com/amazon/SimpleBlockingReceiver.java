package com.amazon;

import com.amazon.Wormhole.NamingThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.amazon.Wormhole.*;

public class SimpleBlockingReceiver implements Receiver {
    private static final Logger logger = LoggerFactory.getLogger(SimpleBlockingReceiver.class);

    private final int port;
    private final int chunkSize;
    private final boolean validate;
    private final int threadCount;
    private Path targetDirectory;
    private Acceptor acceptor;
    private volatile boolean shouldRun = true;

    public SimpleBlockingReceiver(int port, int chunkSize, int threadCount, boolean validate) {
        this.port = port;
        this.chunkSize = chunkSize;
        this.threadCount = threadCount;
        this.validate = validate;
    }

    public SimpleBlockingReceiver() {
        this(DEFAULT_RECEIVER_PORT, DEFAULT_CHUNK_SIZE, DEFAULT_THREAD_COUNT, true);
    }

    public void stop() {
        shouldRun = false;
    }

    @Override
    public void receive() {
        var executor = Executors.newFixedThreadPool(threadCount, new NamingThreadFactory("rx"));
        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setSoTimeout(100);
            while (shouldRun) {
                try {
                    Socket clientSocket = ss.accept();
                    executor.submit(() -> receiveFiles(clientSocket));
                } catch (SocketTimeoutException ignore) {}
            }
            logger.info("Receiver is done.");
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            executor.shutdown();
            try {
                while (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.info("Upload in progress");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void receiveFiles(Socket clientSocket) {
        boolean received;
        do {
            received = receiveFile(clientSocket);
        } while (received && clientSocket.isConnected());
    }

    private boolean receiveFile(Socket clientSocket) {
        try {
            var validator = validate ? new Validator() : null;
            byte[] headerBytes =  new byte[1024];
            int read = clientSocket.getInputStream().read(headerBytes);
            if (read <= 0) {
                logger.warn("{} Did not read header: {}", clientSocket, read);
                return false;
            }

            Header header = Header.decode(headerBytes);
            if (validator != null) {
                validator.expect(header.checksum());
            }

            if (!acceptor.accept(header.sender(), header.filePath(), header.fileLength())) {
                clientSocket.getOutputStream().write(0);
            } else {
                clientSocket.getOutputStream().write(1);
                Path withoutRoot = Wormhole.removeRoot(header.filePath());
                Path filePath = targetDirectory.resolve(withoutRoot);
                Files.createDirectories(filePath.getParent());

                long transferred = 0;
                InputStream upload = clientSocket.getInputStream();
                try (FileOutputStream fout = new FileOutputStream(filePath.toFile())) {
                    byte[] chunk = new byte[chunkSize];
                    while ((read = upload.read(chunk)) != -1) {
                        fout.write(chunk, 0, read);
                        transferred += read;
                        if (validator != null) {
                            validator.update(chunk, 0, read);
                        }
                        if (transferred == header.fileLength()) {
                            break;
                        }
                    }
                }

                if (validator != null) {
                    validator.validate();
                }

                logger.info("{} Received: {}, size: {}", clientSocket, filePath, transferred);
            }
        } catch (IOException e) {
            logger.warn("Error receiving file.", e);
            throw new RuntimeException(e);
        }
        return true;
    }

    public void setTargetDirectory(Path targetDirectory) {
        this.targetDirectory = targetDirectory;
    }

    public void setAcceptor(Acceptor acceptor) {
        this.acceptor = acceptor;
    }
}

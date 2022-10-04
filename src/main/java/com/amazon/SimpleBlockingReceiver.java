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
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.amazon.Wormhole.DEFAULT_CHUNK_SIZE;

public class SimpleBlockingReceiver implements Receiver {
    private static final Logger logger = LoggerFactory.getLogger(SimpleBlockingReceiver.class);

    private final int port;
    private final int chunkSize;
    private final boolean validate;

    private Path targetDirectory;

    private Acceptor acceptor;

    private volatile boolean shouldRun = true;

    public SimpleBlockingReceiver(int port, int chunkSize, boolean validate) {
        this.port = port;
        this.chunkSize = chunkSize;
        this.validate = validate;
    }

    public SimpleBlockingReceiver() {
        this(9000, DEFAULT_CHUNK_SIZE, true);
    }

    public void stop() {
        shouldRun = false;
    }

    @Override
    public void receive() {
        var executor = Executors.newFixedThreadPool(8, new NamingThreadFactory("rx"));
        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setSoTimeout(100);
            while (shouldRun) {
                try {
                    Socket clientSocket = ss.accept();
                    executor.submit(() -> receiveFile(clientSocket));
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

    private void receiveFile(Socket clientSocket) {
        try {
            var validator = validate ? new Validator() : null;
            byte[] headerBytes =  new byte[1024];
            int read = clientSocket.getInputStream().read(headerBytes);
            if (read <= 0) {
                logger.warn("{} Did not read header: {}", clientSocket, read);
                clientSocket.getOutputStream().write(0);
                return;
            }

            Header header = Header.decode(headerBytes);
            if (validator != null) {
                validator.expect(header.checksum());
            }

            if (!acceptor.accept(header.sender(), header.filePath(), header.fileLength())) {
                clientSocket.getOutputStream().write(0);
            } else {
                clientSocket.getOutputStream().write(1);
                Path filePath = targetDirectory.resolve(header.filePath());
                long transferred = 0;
                try (InputStream upload = clientSocket.getInputStream();
                     FileOutputStream fout = new FileOutputStream(filePath.toFile())) {
                    byte[] chunk = new byte[chunkSize];
                    while ((read = upload.read(chunk)) != -1) {
                        fout.write(chunk, 0, read);
                        transferred += read;
                        if (validator != null) {
                            validator.update(chunk, 0, read);
                        }
                    }
                }

                if (validator != null) {
                    validator.validate();
                }

                logger.info("{} Received: {}, size: {}", clientSocket, filePath, transferred);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setTargetDirectory(Path targetDirectory) {
        this.targetDirectory = targetDirectory;
    }

    public void setAcceptor(Acceptor acceptor) {
        this.acceptor = acceptor;
    }
}

package com.amazon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static com.amazon.Wormhole.*;
import static com.amazon.Wormhole.DEFAULT_CHUNK_SIZE;

public class SimpleBlockingSender implements Sender {
    private static final Logger logger = LoggerFactory.getLogger(SimpleBlockingSender.class);
    private final String senderName;
    private final int chunkSize;
    private final boolean validate;

    public SimpleBlockingSender(String sender, int chunkSize, boolean validate) {
        this.senderName = sender;
        this.chunkSize = chunkSize;
        this.validate = validate;
    }

    public SimpleBlockingSender(String sender) {
        this(sender, DEFAULT_CHUNK_SIZE, true);
    }

    @Override
    public void send(File source, String host, int port) {
        if (!source.isDirectory()) {
            sendSingle(source, host, port);
        } else {
            try {
                sendMultiple(source, host, port);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        logger.info("Send is complete.");
    }

    private void sendMultiple(File source, String host, int port) throws IOException {
        var queue = new LinkedBlockingDeque<File>();
        queue.addLast(source);
        ExecutorService executor = null;
        try  {
            executor = Executors.newFixedThreadPool(8, new NamingThreadFactory("Sender"));
            for (int i = 0; i < 8; ++i) {
                executor.submit(() -> processWork(queue, host, port));
            }
        } finally {
            if (executor != null) {
                try {
                    executor.shutdown();
                    while (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                        logger.info("Upload in progress");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void processWork(LinkedBlockingDeque<File> files, String host, int port) {
        Socket socket = null;
        try {
            while (true) {
                try {
                    File file = files.pollLast(10, TimeUnit.MILLISECONDS);
                    if (file == null) {
                        break;
                    }

                    if (!file.isDirectory()) {
                        if (socket == null) {
                            socket = new Socket(host, port);
                        }
                        transfer(file, socket);
                    } else {
                        File[] children = file.listFiles();
                        if (children != null && children.length > 0) {
                            for (var child : children) {
                                files.addLast(child);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignore) {}
            }
        }
    }

    private void sendSingle(File source, String host, int port) {
        try (Socket s = new Socket(host, port)) {
            transfer(source, s);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void transfer(File source, Socket s) {
        try (var fin = new FileInputStream(source)) {
            var checksum = validate ? hash(source) : null;
            var header = new Header(senderName, source.getName(), source.length(), checksum);
            logger.info("Sending upload request: {}", header);
            s.getOutputStream().write(header.encode());

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

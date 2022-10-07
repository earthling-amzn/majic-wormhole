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
import java.util.concurrent.atomic.LongAdder;

import static com.amazon.Wormhole.*;

public class SimpleBlockingSender implements Sender {
    private static final Logger logger = LoggerFactory.getLogger(SimpleBlockingSender.class);
    private final String senderName;
    private final int chunkSize;
    private final boolean validate;
    private final int threadCount;
    private final LongAdder filesTransferred = new LongAdder();
    private final LongAdder bytesTransferred = new LongAdder();

    public SimpleBlockingSender(String sender, int chunkSize, int threadCount, boolean validate) {
        this.senderName = sender;
        this.chunkSize = chunkSize;
        this.validate = validate;
        this.threadCount = threadCount;
    }

    public SimpleBlockingSender(String sender) {
        this(sender, DEFAULT_CHUNK_SIZE, DEFAULT_THREAD_COUNT, true);
    }

    @Override
    public long getFilesTransferred() {
        return filesTransferred.longValue();
    }

    @Override
    public long getBytesTransferred() {
        return bytesTransferred.longValue();
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
    }

    private void sendMultiple(File source, String host, int port) throws IOException {
        var queue = new LinkedBlockingDeque<File>();
        queue.addLast(source);
        ExecutorService executor = null;
        try  {
            executor = Executors.newFixedThreadPool(threadCount, new NamingThreadFactory("tx"));
            for (int i = 0; i < threadCount; ++i) {
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
        byte[] chunk = new byte[chunkSize];
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
                        transfer(file, socket, chunk);
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
            transfer(source, s, new byte[chunkSize]);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void transfer(File source, Socket s, byte[] chunk) {
        try (var fin = new FileInputStream(source)) {
            var checksum = validate ? hash(source) : null;
            var header = new Header(senderName, source.getAbsolutePath(), source.length(), checksum);
            byte[] encoded = header.encode();
            logger.debug("Sending upload request: {} {}", encoded.length, header);
            s.getOutputStream().write(encoded);

            // Wait for response for receiver to proceed.
            int proceed = s.getInputStream().read();
            if (proceed != 1) {
                logger.warn("Cannot proceed with uploaded.");
                return;
            }

            int transferred = 0;
            int read;
            while ((read = fin.read(chunk)) != -1) {
                s.getOutputStream().write(chunk, 0, read);
                transferred += read;
            }
            logger.debug("Upload complete: {}", header.filePath());
            filesTransferred.increment();
            bytesTransferred.add(source.length());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

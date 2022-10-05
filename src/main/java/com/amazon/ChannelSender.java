package com.amazon;

import com.amazon.Wormhole.NamingThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import static com.amazon.Wormhole.DEFAULT_CHUNK_SIZE;
import static com.amazon.Wormhole.DEFAULT_THREAD_COUNT;

public class ChannelSender implements Sender {
    private static final Logger logger = LoggerFactory.getLogger(ChannelSender.class);

    private final String senderName;
    private final int chunkSize;
    private final boolean validate;
    private final int threadCount;
    private final LongAdder filesTransferred = new LongAdder();
    private final LongAdder bytesTransferred = new LongAdder();

    public ChannelSender(String senderName, int chunkSize, int threadCount, boolean validate) {
        this.senderName = senderName;
        this.chunkSize = chunkSize;
        this.validate = validate;
        this.threadCount = threadCount;
    }

    public ChannelSender(String senderName) {
        this(senderName, DEFAULT_CHUNK_SIZE, DEFAULT_THREAD_COUNT, true);
    }

    @Override
    public long getFilesTransferred() {
        return filesTransferred.longValue();
    }

    @Override
    public long getBytesTransferred() {
        return bytesTransferred.longValue();
    }

    public void send(File source, String host, int port) {
        if (!source.isDirectory()) {
            sendSingle(source, host, port);
        } else {
            sendMultiple(source, host, port);
        }
    }

    private void sendMultiple(File source, String host, int port) {
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
        SocketChannel socket = null;
        try {
            while (true) {
                try {
                    File file = files.pollLast(10, TimeUnit.MILLISECONDS);
                    if (file == null) {
                        break;
                    }

                    if (!file.isDirectory()) {
                        if (socket == null) {
                            socket = SocketChannel.open(new InetSocketAddress(host, port));
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
            logger.warn("Error sending file", e);
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
        try (SocketChannel socket = SocketChannel.open(new InetSocketAddress(host, port))) {
            transfer(source, socket);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void transfer(File source, SocketChannel socket) throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(source)) {

            var checksum = validate ? Wormhole.hash(source) : null;
            var header = new Header(senderName, source.getAbsolutePath(), source.length(), checksum);
            logger.debug("Sending upload request: {}", header);
            socket.write(ByteBuffer.wrap(header.encode()));

            var proceed = ByteBuffer.allocate(10);
            socket.read(proceed);
            proceed.flip();

            byte accepted = proceed.get();
            if (accepted != 1) {
                logger.warn("Cannot proceed with upload: {}", accepted);
                return;
            }

            FileChannel channel = fileInputStream.getChannel();
            long readFrom = 0;
            while (true) {
                long transferred = channel.transferTo(readFrom, chunkSize, socket);
                if (transferred <= 0) {
                    break;
                }
                readFrom += transferred;
            }
            logger.debug("Upload complete: {}", header.filePath());
            filesTransferred.increment();
            bytesTransferred.add(source.length());
        }
    }
}

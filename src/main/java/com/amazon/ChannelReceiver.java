package com.amazon;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class ChannelReceiver implements Receiver {
    private final int port;
    private final ByteBuffer buffer;
    private final byte[] headers = new byte[1024];

    public ChannelReceiver(int port) {
        this.port = port;
        this.buffer = ByteBuffer.allocateDirect(Receiver.CHUNK_SIZE);
    }

    private Path targetDirectory;

    private SimpleBlockingReceiver.Acceptor acceptor;

    @Override
    public void receive() {
        try (ServerSocketChannel ss = ServerSocketChannel.open()) {
            ss.bind(new InetSocketAddress(port));
            var clientSocket = ss.accept();

            var headerBuffer = ByteBuffer.wrap(headers);
            int read = clientSocket.read(headerBuffer);
            headerBuffer.flip();
            var header = new String(headers, 0, read, StandardCharsets.UTF_8);
            String[] parts = header.split(":", 3);
            String sender = parts[0], filename = parts[2].stripTrailing();
            int length = Integer.parseInt(parts[1]);

            if (!acceptor.accept(sender, filename, length)) {
                clientSocket.write(ByteBuffer.wrap(new byte[] {0}));
            } else {
                clientSocket.write(ByteBuffer.wrap(new byte[] {1}));
                Path filePath = targetDirectory.resolve(filename);
                long writeTo = 0;
                buffer.clear();
                System.out.println("Creating file at: <" + filePath + ">");
                try (var fout = new FileOutputStream(filePath.toFile());
                     var fileChannel = fout.getChannel()) {

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
                        writeTo += wrote;
                        buffer.clear();
                    }
                }
                System.out.println("Received: " + writeTo);
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

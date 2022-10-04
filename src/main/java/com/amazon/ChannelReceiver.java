package com.amazon;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class ChannelReceiver implements Receiver {
    private final int port;
    private final int chunkSize;
    private final ByteBuffer buffer;
    private final boolean validate;
    private final byte[] headers = new byte[1024];

    public ChannelReceiver(int port, int chunkSize, boolean validate) {
        this.port = port;
        this.chunkSize = chunkSize;
        this.buffer = ByteBuffer.allocateDirect(chunkSize);
        this.validate = validate;
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
            if (read <= 0) {
                System.out.println("Did not read header");
                return;
            }

            var header = Header.decode(headers);

            MessageDigest md = null;
            if (validate) {
                if (header.checksum() != null) {
                    md = MessageDigest.getInstance("MD5");
                } else {
                    System.out.println("Receiver requires checksum.");
                    clientSocket.write(ByteBuffer.wrap(new byte[]{0}));
                    return;
                }
            }

            if (!acceptor.accept(header.sender(), header.filePath(), header.fileLength())) {
                clientSocket.write(ByteBuffer.wrap(new byte[] {0}));
            } else {
                clientSocket.write(ByteBuffer.wrap(new byte[] {1}));
                Path filePath = targetDirectory.resolve(header.filePath());
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
                        if (md != null) {
                            buffer.flip();
                            md.update(buffer);
                        }

                        writeTo += wrote;
                        buffer.clear();
                    }
                }
                System.out.println("Received: " + writeTo);
                if (md != null) {
                    byte[] digest = md.digest();
                    if (Arrays.equals(digest, header.checksum())) {
                        System.out.println("Checksums match.");
                    } else {
                        System.err.printf("Checksum mismatch: Expected: %s, Received: %s\n",
                                Wormhole.toHex(header.checksum()), Wormhole.toHex(digest));
                    }
                }
            }
        } catch (IOException | NoSuchAlgorithmException e) {
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

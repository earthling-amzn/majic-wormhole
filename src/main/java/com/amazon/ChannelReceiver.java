package com.amazon;

import java.io.FileOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Path;

public class ChannelReceiver implements Receiver {
    private final int port;
    private final int chunkSize;
    private final ByteBuffer buffer;
    private final Validator validator;
    private final byte[] headers = new byte[1024];

    public ChannelReceiver(int port, int chunkSize, Validator validator) {
        this.port = port;
        this.chunkSize = chunkSize;
        this.buffer = ByteBuffer.allocateDirect(chunkSize);
        this.validator = validator;
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
                System.out.println("Received: " + writeTo);
            }
        } catch (Exception e) {
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

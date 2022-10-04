package com.amazon;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;

public class SimpleBlockingReceiver implements Receiver {
    private final int port;
    private final int chunkSize;
    private final Validator validator;

    public SimpleBlockingReceiver(int port, int chunkSize, Validator validator) {
        this.port = port;
        this.chunkSize = chunkSize;
        this.validator = validator;
    }

    private Path targetDirectory;

    private Acceptor acceptor;

    @Override
    public void receive() {
        try (ServerSocket ss = new ServerSocket(port)) {
            Socket clientSocket = ss.accept();

            byte[] headerBytes =  new byte[1024];
            int read = clientSocket.getInputStream().read(headerBytes);
            if (read <= 0) {
                System.out.println("Did not read header");
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

                System.out.println("Received: " + transferred);
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

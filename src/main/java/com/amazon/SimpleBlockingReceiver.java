package com.amazon;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class SimpleBlockingReceiver implements Receiver {
    private final int port;
    private final int chunkSize;
    private final boolean validate;

    public SimpleBlockingReceiver(int port, int chunkSize, boolean validate) {
        this.port = port;
        this.chunkSize = chunkSize;
        this.validate = validate;
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
                return;
            }

            Header header = Header.decode(headerBytes);
            MessageDigest md = null;
            if (validate) {
                if (header.checksum() != null) {
                    md = MessageDigest.getInstance("MD5");
                } else {
                    System.out.println("Receiver requires checksum.");
                    clientSocket.getOutputStream().write(0);
                    return;
                }
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
                        if (md != null) {
                            md.update(chunk, 0, read);
                        }
                    }
                }
                System.out.println("Received: " + transferred);
                if (md != null) {
                    byte[] digest = md.digest();
                    if (Arrays.equals(digest, header.checksum())) {
                        System.out.println("Checksums match.");
                    } else {
                        System.err.printf("Checksum mismatch: Expected: %s, Received: %s\n",
                                Wormhole.toHex(header.checksum()), Wormhole.toHex(digest));
                        Files.delete(filePath);
                        throw new IllegalStateException("Invalid digest.");
                    }
                }
            }
        } catch (IOException | NoSuchAlgorithmException e) {
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

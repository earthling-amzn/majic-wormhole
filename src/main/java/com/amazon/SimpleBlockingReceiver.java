package com.amazon;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;

public class SimpleBlockingReceiver implements Receiver {
    private final int port;

    public SimpleBlockingReceiver(int port) {
        this.port = port;
    }

    private Path targetDirectory;

    private Acceptor acceptor;

    @Override
    public void receive() {
        try (ServerSocket ss = new ServerSocket(port)) {
            Socket clientSocket = ss.accept();

            var reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            String header = reader.readLine();
            String[] parts = header.split(":", 3);
            String sender = parts[0], filename = parts[2];
            int length = Integer.parseInt(parts[1]);

            if (!acceptor.accept(sender, filename, length)) {
                clientSocket.getOutputStream().write(0);
            } else {
                clientSocket.getOutputStream().write(1);
                Path filePath = targetDirectory.resolve(filename);
                int read;
                long transferred = 0;
                try (InputStream upload = clientSocket.getInputStream();
                     FileOutputStream fout = new FileOutputStream(filePath.toFile())) {
                    byte[] chunk = new byte[CHUNK_SIZE];
                    while ((read = upload.read(chunk)) != -1) {
                        fout.write(chunk, 0, read);
                        transferred += read;
                    }
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

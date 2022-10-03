package com.amazon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;

public class Receiver {
    private static final Logger logger = LoggerFactory.getLogger(Receiver.class);
    private final int port;

    public Receiver(int port) {
        this.port = port;
    }

    interface Acceptor {
        boolean accept(String sender, String filename, long length);
    }

    private Path targetDirectory;

    private Acceptor acceptor;

    void handleFileUpload() {
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
                    byte[] chunk = new byte[10 * 1024 * 1024];
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

    public static boolean deferToUser(String sender, String filename, long length) {
        while (true) {
            System.out.printf("Receive file named %s (%s bytes) from %s? y/n\n", filename, length, sender);
            var reader = new BufferedReader(new InputStreamReader(System.in));
            try {
                String input = reader.readLine();
                if ("y".equals(input)) {
                    return true;
                }
                if ("n".equals(input)) {
                    return false;
                }
                System.out.println("Please type 'y' or 'n'.");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}

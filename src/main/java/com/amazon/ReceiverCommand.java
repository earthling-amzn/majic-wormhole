package com.amazon;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.amazon.Wormhole.*;
import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;

@Command(name = "recv")
public class ReceiverCommand implements Runnable {

    @Option(names = {"-r", "--registrar"}, description = "Registrar address", defaultValue = "http://localhost:8080")
    String registrarAddress;

    @Option(names = {"-u", "--username"}, description = "Your receiver name.", required = true)
    String receiverName;

    @Option(names = {"-D", "--target-dir"}, description = "Save file here, override suggested file name")
    Path targetDirectory;

    @Option(names = {"-y", "--accept"}, description = "Accept all files without prompting (for testing).")
    boolean acceptAll = false;

    @Option(names = {"-f", "--forever"}, description = "Run forever.")
    boolean runForever = false;

    @Option(names = {"-p", "--port"}, description = "Port to listen on.")
    int port = DEFAULT_RECEIVER_PORT;

    @Option(names = {"-d", "--direct"}, description = "Use direct buffers for file transfer.")
    boolean useDirect = false;

    @Option(names = {"-c", "--chunk"}, description = "Chunk size for transfer buffer in bytes")
    int chunkSize = DEFAULT_CHUNK_SIZE;

    @Option(names = {"-v", "--validate"}, description = "Validate checksum (md5) of received file")
    boolean validate = false;

    @Option(names = {"-t", "--threads"}, description = "Number of threads to use for sending files")
    int threadCount = DEFAULT_THREAD_COUNT;

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

    @Override
    public void run() {
        try {
            Files.createDirectories(targetDirectory);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        createRegistration();

        Receiver receiver = getReceiver();
        receiver.setTargetDirectory(targetDirectory);
        if (acceptAll) {
            receiver.setAcceptor((username, filename, length) -> true);
        } else {
            receiver.setAcceptor(ReceiverCommand::deferToUser);
        }

        do {
            receiver.receive();
        } while (runForever);
    }

    private Receiver getReceiver() {
        return useDirect
                ? new ChannelReceiver(port, chunkSize, threadCount, validate)
                : new SimpleBlockingReceiver(port, chunkSize, threadCount, validate);
    }

    private Registration createRegistration() {
        try (var client = HttpClient.create(new URL(registrarAddress))) {
            var request = HttpRequest.GET("/register");
            request.getParameters().add("username", receiverName);
            request.getParameters().add("target", String.valueOf(port));
            return client.toBlocking().retrieve(request, Registration.class);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
}

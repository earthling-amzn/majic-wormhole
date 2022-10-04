package com.amazon;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.amazon.Wormhole.DEFAULT_CHUNK_SIZE;

@Command(name = "send", description = "...",
        mixinStandardHelpOptions = true)
public class SenderCommand implements Runnable {

    @Option(names = {"-f", "--file"}, description = "A file to transfer", required = true)
    Path fileToSend;

    @Option(names = {"-r", "--registrar"}, description = "Registrar address", defaultValue = "http://localhost:8080")
    String registrarAddress;

    @Option(names = {"-s", "--sender"}, description = "Your name, will be shown to receiver")
    String senderName;

    @Option(names = {"-t", "--receiver"}, description = "The name of a registered receiver")
    String receiverName;

    @Option(names = {"-d", "--direct"}, description = "Use direct buffers for file transfer.")
    boolean useDirect = false;

    @Option(names = {"-c", "--chunk"}, description = "Chunk size for transfer buffer in bytes")
    int chunkSize = DEFAULT_CHUNK_SIZE;

    @Override
    @Command(name = "send")
    public void run() {
        long start = System.nanoTime();
        if (!Files.isRegularFile(fileToSend)) {
            printError("Not a regular file: " + fileToSend);
            System.exit(2);
        }

        // Try to get the address of the receiver so we know where to send the file
        var registration = getReceiverRegistration();

        try {
            var sender = getSender();
            start = System.nanoTime();
            sender.send(fileToSend.toFile(), registration.address(), registration.port());
        } finally {
            long end = System.nanoTime();
            String message = CommandLine.Help.Ansi.AUTO.string("@|bold,green Transfer complete. |@");
            System.out.println(message);
            System.out.printf("Transfer time: %.5fs.\n", (end - start) / 1_000_000_000d);
        }
    }

    private Sender getSender() {
        return useDirect
                ? new ChannelSender(senderName, chunkSize)
                : new SimpleBlockingSender(senderName, chunkSize);
    }

    private void printError(String message) {
        String error = CommandLine.Help.Ansi.AUTO.string("@|bold,red " + message + " |@");
        System.out.println(error);
    }

    private Registration getReceiverRegistration() {
        try (var client = HttpClient.create(new URL(registrarAddress))) {
            var request = HttpRequest.GET("/fetch");
            request.getParameters().add("receiver", receiverName);
            return client.toBlocking().retrieve(request, Registration.class);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
}

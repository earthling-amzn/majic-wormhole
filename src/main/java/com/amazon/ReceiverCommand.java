package com.amazon;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;

@Command(name = "recv")
public class ReceiverCommand implements Runnable {

    @Option(names = {"-r", "--registrar"}, description = "Registrar address", defaultValue = "http://localhost:8080")
    String registrarAddress;

    @Option(names = {"-p", "--username"}, description = "Your receiver name.", required = true)
    String receiverName;

    @Option(names = {"-f", "--target-dir"}, description = "Save file here, override suggested file name")
    Path targetDirectory;

    @Option(names = {"-y", "--accept"}, description = "Accept all files without prompting (for testing).")
    boolean acceptAll = false;

    @Override
    public void run() {
        try {
            Files.createDirectories(targetDirectory);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        var receiver = new Receiver(9000);
        createRegistration(9000);
        receiver.setTargetDirectory(targetDirectory);
        if (acceptAll) {
            receiver.setAcceptor((username, filename, length) -> true);
        } else {
            receiver.setAcceptor(Receiver::deferToUser);
        }

        //noinspection InfiniteLoopStatement
        while (true) {
            receiver.handleFileUpload();
        }
    }

    private Registration createRegistration(int port) {
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

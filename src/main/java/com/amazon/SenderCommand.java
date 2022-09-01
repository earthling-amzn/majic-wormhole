package com.amazon;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.server.HttpServerConfiguration;
import jakarta.inject.Inject;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

@Command(name = "send", description = "...",
        mixinStandardHelpOptions = true)
public class SenderCommand implements Runnable {

    @Inject
    HttpServerConfiguration serverConfiguration;

    @Inject
    Sender sender;

    @Option(names = {"-f", "--file"}, description = "A file to transfer", required = true)
    Path fileToSend;

    @Option(names = {"-r", "--registrar"}, description = "Registrar address", defaultValue = "http://localhost:8080")
    String registrarAddress;

    @Override
    @Command(name = "send")
    public void run() {
        if (!Files.isRegularFile(fileToSend)) {
            String error = CommandLine.Help.Ansi.AUTO.string("@|bold,red Not a regular file: |@" + fileToSend);
            System.out.println(error);
            System.exit(2);
        }

        try (var client = HttpClient.create(new URL(registrarAddress))) {
            String serverPort = String.valueOf(serverConfiguration.getPort().orElseThrow());
            var request = HttpRequest.GET("/register");
            request.getParameters().add("port", serverPort);
            Registration registration = client.toBlocking().retrieve(request, Registration.class);
            sender.setRegistration(registration);
            sender.setFileToTransfer(fileToSend);
            String success = CommandLine.Help.Ansi.AUTO.string("@|green,bold Registration succeeded: |@" + registration.passcode());
            System.out.println(success);
            System.out.println("Waiting for receiver, CTRL+C to cancel.");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
}

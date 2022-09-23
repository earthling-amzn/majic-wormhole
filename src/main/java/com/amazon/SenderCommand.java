package com.amazon;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

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

    @Override
    @Command(name = "send")
    public void run() {
        if (!Files.isRegularFile(fileToSend)) {
            printError("Not a regular file: " + fileToSend);
            System.exit(2);
        }

        // Try to get the address of the receiver so we know where to send the file
        var registration = getReceiverRegistration();
        URL url = getReceiverUrl(registration);

        // Now try to send it!
        try (var client = HttpClient.create(url)) {
            var request = createUploadRequest();
            client.toBlocking().retrieve(request);
            CommandLine.Help.Ansi.AUTO.string("@|bold,green Transfer complete. |@");
        } catch (HttpClientResponseException e) {
            if (e.getStatus() == HttpStatus.NOT_ACCEPTABLE) {
                printError("Receiver rejected the file.");
            } else if (e.getStatus() == HttpStatus.INTERNAL_SERVER_ERROR) {
                printError("Server encountered an error.");
            } else {
                printError("Something unexpected happened: " + e.getStatus());
            }
        }
    }

    private static URL getReceiverUrl(Registration registration) {
        try {
            return new URL("http", registration.address(), (int) registration.port(), "/");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private void printError(String message) {
        String error = CommandLine.Help.Ansi.AUTO.string("@|bold,red " + message + " |@");
        System.out.println(error);
    }

    private MutableHttpRequest<MultipartBody> createUploadRequest() {
        File transferFile = fileToSend.toFile();
        MultipartBody formData = MultipartBody.builder()
                .addPart("sender", senderName)
                .addPart("upload", transferFile)
                .addPart("length", String.valueOf(transferFile.length()))
                .build();
        return HttpRequest.POST("/file", formData)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE);
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

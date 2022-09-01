package com.amazon;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.exceptions.HttpStatusException;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;

@Command(name = "recv")
public class ReceiverCommand implements Runnable {

    @Option(names = {"-r", "--registrar"}, description = "Registrar address", defaultValue = "http://localhost:8080")
    String registrarAddress;

    @Option(names = {"-p", "--passcode"}, description = "The magic wormhole passcode you were given.", required = true)
    String passcode;

    @Option(names = {"-f", "--file"}, description = "Save file here, override suggested file name")
    String fileName;

    @Override
    public void run() {
        Registration registration = getRegistration();

        try (var client = HttpClient.create(new URL("http", registration.address(), (int)registration.port(), ""))) {
            var request = HttpRequest.GET("/file");
            request.getParameters().add("passcode", passcode);
            var response = client.toBlocking().exchange(request, byte[].class);
            byte[] content = response.body();
            if (content == null) {
                throw new IllegalArgumentException("File content is missing.");
            }
            if (fileName == null) {
                fileName = getAttachmentName(response);
            }
            try (FileOutputStream fout = new FileOutputStream(fileName)) {
                fout.write(content);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Registration getRegistration() {
        try (var client = HttpClient.create(new URL(registrarAddress))) {
            var request = HttpRequest.GET("/fetch");
            request.getParameters().add("passcode", passcode);
            return client.toBlocking().retrieve(request, Registration.class);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private String getAttachmentName(HttpResponse<byte[]> response) {
        String disposition = response.header(HttpHeaders.CONTENT_DISPOSITION);
        if (disposition == null) {
            throw new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Server didn't send content disposition header.");
        }
        // Content-Disposition -> attachment; filename="test7209689700109372498test"; filename*=utf-8''test7209689700109372498test
        var pattern = Pattern.compile("filename=\"(.*?)\"");
        Matcher matcher = pattern.matcher(disposition);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Server didn't send valid content disposition header.");
    }
}

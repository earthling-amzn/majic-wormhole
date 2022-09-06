package com.amazon;

import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.http.exceptions.HttpStatusException;
import reactor.core.publisher.Flux;

import java.io.FileNotFoundException;
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

    private FileOutputStream outputStream;

    @Override
    public void run() {
        Registration registration = getRegistration();

        HttpClientConfiguration configuration = new DefaultHttpClientConfiguration();
        configuration.setMaxContentLength(0);
        URL url = getUrl(registration);
        try (var client = StreamingHttpClient.create(url, configuration)) {
            var request = HttpRequest.GET("/file");
            request.getParameters().add("passcode", passcode);
            Flux.from(client.exchangeStream(request)).map(this::handleChunk).blockLast();
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    System.out.println("Error closing stream: " + e.getMessage());
                }
            }
        }
    }

    private static URL getUrl(Registration registration) {
        try {
            return new URL("http", registration.address(), (int) registration.port(), "");
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private long handleChunk(HttpResponse<ByteBuffer<?>> response) {
        if (fileName == null) {
            fileName = getAttachmentName(response);
        }

        if (outputStream == null) {
            try {
                outputStream = new FileOutputStream(fileName);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        ByteBuffer<?> body = response.body();
        if (body == null) {
            throw new IllegalArgumentException("Response is missing content.");
        }

        try {
            byte[] bytes = body.toByteArray();
            outputStream.write(bytes);
            return bytes.length;
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

    private String getAttachmentName(HttpResponse<?> response) {
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

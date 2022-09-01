package com.amazon;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
public class SenderTest {
    @Inject
    Sender sender;

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    public void testIncompleteRegistration() {
        try {
            sender.setRegistration(null);
            var request = HttpRequest.GET("/file");
            request.getParameters().add("passcode", "passcode");
            client.toBlocking().retrieve(request);
            fail("Expected exception");
        } catch (HttpClientResponseException e) {
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getStatus());
        }
    }

    @Test
    public void testInvalidPasscode() {
        try {
            var registration = new Registration("127.0.0.1", 7070, "sorry-charlie");
            sender.setRegistration(registration);
            var request = HttpRequest.GET("/file");
            request.getParameters().add("passcode", "passcode");
            client.toBlocking().retrieve(request);
            fail("Expected exception");
        } catch (HttpClientResponseException e) {
            assertEquals(HttpStatus.UNAUTHORIZED, e.getStatus());
        }
    }

    @Test
    public void testTransferFile() {
        String fileContents = "Hello from the other side.";
        var toTransfer = createTransferFile(fileContents);
        var registration = new Registration("127.0.0.1", 7070, "sorry-charlie");
        sender.setRegistration(registration);
        sender.setFileToTransfer(toTransfer.toPath());
        var request = HttpRequest.GET("/file");
        request.getParameters().add("passcode", "sorry-charlie");
        var response = client.toBlocking().exchange(request, byte[].class);
        var received = response.body();
        var attachmentName = getAttachmentName(response);
        assertNotNull(received, "No content received");
        assertEquals(fileContents, new String(received));
        assertEquals(toTransfer.toPath().getFileName().toString(), attachmentName);
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

    private File createTransferFile(String contents) {
        try {
            File f = File.createTempFile("test", "test");
            f.deleteOnExit();
            try (FileWriter writer = new FileWriter(f)) {
                writer.write(contents);
            }
            return f;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

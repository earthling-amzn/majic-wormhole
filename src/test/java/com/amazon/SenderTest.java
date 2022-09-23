package com.amazon;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
public class SenderTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    Receiver receiver;

    @Test
    public void testSendToReceiver() throws HttpClientResponseException, IOException {
        // Normally, the sender would have the registry send the listening
        // port of the receiver (big security hole!), but this test is running
        // the registry and the receiver on the same interface and the http client
        // is already automagically configured to find it.
        Path targetDirectory = Files.createTempDirectory("transfer-test");
        receiver.setAcceptor((username, filename, length) -> true);
        receiver.setTargetDirectory(targetDirectory);

        File transferFile = createTransferFile("Don't get too close!");
        MutableHttpRequest<MultipartBody> upload = createUploadRequest(transferFile);

        var response = client.toBlocking().retrieve(upload, HttpStatus.class);
        assertEquals(HttpStatus.OK, response);
        assertTrue(Files.isRegularFile(targetDirectory.resolve(transferFile.getName())));
    }

    @Test
    public void testRejectedByReceiver() throws Exception {
        Path targetDirectory = Files.createTempDirectory("transfer-test");
        receiver.setAcceptor((username, filename, length) -> false);
        receiver.setTargetDirectory(targetDirectory);

        try {
            File transferFile = createTransferFile("To my fantasy!");
            MutableHttpRequest<MultipartBody> upload = createUploadRequest(transferFile);
            client.toBlocking().retrieve(upload, HttpStatus.class);
            fail("Expected failure here.");
        } catch (HttpClientResponseException  e) {
            assertEquals(HttpStatus.NOT_ACCEPTABLE, e.getStatus());
            assertTrue(isDirectoryEmpty(targetDirectory));
        }
    }

    private boolean isDirectoryEmpty(Path targetDirectory) throws IOException {
        try (Stream<Path> files = Files.list(targetDirectory)) {
            return files.toList().isEmpty();
        }
    }

    private static MutableHttpRequest<MultipartBody> createUploadRequest(File transferFile) {
        MultipartBody formData = MultipartBody.builder()
                .addPart("sender", "Captain Fantasy")
                .addPart("upload", transferFile)
                .addPart("length", "19")
                .build();
        return HttpRequest.POST("/file", formData)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE);
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

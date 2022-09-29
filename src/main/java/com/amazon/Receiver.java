package com.amazon;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.StreamingFileUpload;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;

@Controller
public class Receiver {
    private static final Logger logger = LoggerFactory.getLogger(Receiver.class);

    interface Acceptor {
        boolean accept(String sender, String filename, long length);
    }

    private Path targetDirectory;

    private Acceptor acceptor;

    @Post(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA)
    Publisher<MutableHttpResponse<?>> handleFileUpload(StreamingFileUpload upload, int length, String sender) {
        logger.info("Handle upload from: {}", sender);
        logger.info("Defined size: {}", upload.getDefinedSize());
        logger.info("Size: {}", upload.getSize());
        logger.info("Name: {}", upload.getName());
        logger.info("Length: {}", length);
        logger.info("File name: {}", upload.getFilename());
        if (acceptor.accept(sender, upload.getFilename(), length)) {
            Path target = targetDirectory.resolve(upload.getFilename());
            var internalError = HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Something bad happened");
            var uploadSucceeded = HttpResponse.ok("Uploaded " + upload.getDefinedSize());
            return Flux.from(upload.transferTo(target.toFile()))
                    .map(success -> success ? uploadSucceeded : internalError)
                    .onErrorReturn(internalError);
        }
        return Flux.just(HttpResponse.status(HttpStatus.NOT_ACCEPTABLE));
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

package com.amazon;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.StreamingFileUpload;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;

@Controller
public class Receiver {

    interface Acceptor {
        boolean accept(String sender, String filename, long length);
    }

    private Path targetDirectory;

    private Acceptor acceptor;

    @Post(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA)
    HttpStatus handleFileUpload(StreamingFileUpload upload, int length, String sender) {
        System.out.println("Handle upload from: " + sender);
        System.out.println("Defined size: " + upload.getDefinedSize());
        System.out.println("Size: " + upload.getSize());
        System.out.println("Name: " + upload.getName());
        System.out.println("Length: " + length);
        System.out.println("File name: " + upload.getFilename());

        if (acceptor.accept(sender, upload.getFilename(), length)) {
            Path target = targetDirectory.resolve(upload.getFilename());
            var succeeded = Mono.from(upload.transferTo(target.toFile())).block();
            System.out.println("Wrote file to: " + target);
            return Boolean.TRUE.equals(succeeded) ? HttpStatus.OK : HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return HttpStatus.NOT_ACCEPTABLE;
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

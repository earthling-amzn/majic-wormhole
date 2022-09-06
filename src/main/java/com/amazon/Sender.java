package com.amazon;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.types.files.StreamedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;

@Controller
public class Sender {
    private static final Logger logger = LoggerFactory.getLogger("send");
    private Registration registration;
    private Path fileToTransfer;

    @Get("/file")
    public StreamedFile transfer(@QueryValue("passcode") String passcode) {
        if (registration == null) {
            throw new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Registration not complete.");
        }

        if (!registration.passcode().equals(passcode)) {
            throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Not authorized");
        }

        try {
            FileInputStream inputStream = new FileInputStream(fileToTransfer.toFile());
            return new StreamedFile(inputStream, MediaType.APPLICATION_OCTET_STREAM_TYPE).attach(fileToTransfer.getFileName().toString());
        } catch (IOException e) {
            throw new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not open file to send.");
        } finally {
            logger.info("Transfer of {} complete.", fileToTransfer);
        }
    }

    public void setRegistration(Registration registration) {
        this.registration = registration;
    }

    public void setFileToTransfer(Path fileToTransfer) {
        this.fileToTransfer = fileToTransfer;
    }
}

package com.amazon;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.types.files.SystemFile;

import java.nio.file.Path;

@Controller("/")
public class Sender {
    private SenderCommand.Registration registration;
    private Path fileToTransfer;

    @Get("/file")
    public SystemFile transfer(@QueryValue("passcode") String passcode) {
        if (registration == null) {
            throw new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Registration not complete.");
        }

        if (!registration.passcode().equals(passcode)) {
            throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Not authorized");
        }

        return new SystemFile(fileToTransfer.toFile(), MediaType.APPLICATION_OCTET_STREAM_TYPE).attach(fileToTransfer.getFileName().toString());
    }

    public void setRegistration(SenderCommand.Registration registration) {
        this.registration = registration;
    }

    public void setFileToTransfer(Path fileToTransfer) {
        this.fileToTransfer = fileToTransfer;
    }
}

package com.amazon;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.http.server.HttpServerConfiguration;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "sender", description = "...",
        mixinStandardHelpOptions = true)
public class SenderCommand implements Runnable {

    @Inject
    HttpServerConfiguration serverConfiguration;

    @Option(names = {"-v", "--verbose"}, description = "...")
    boolean verbose;

    public static void main(String[] args) throws Exception {
        PicocliRunner.run(SenderCommand.class, args);
    }

    public void run() {
        // business logic here
        if (verbose) {
            System.out.println("Hi!");
        }
    }
}

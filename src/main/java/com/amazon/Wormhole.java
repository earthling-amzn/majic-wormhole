package com.amazon;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.Micronaut;
import picocli.CommandLine;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@CommandLine.Command(name="wormhole", subcommands = {SenderCommand.class, ReceiverCommand.class})
public class Wormhole implements Runnable {
    public static final int DEFAULT_CHUNK_SIZE = 10 * 1024 * 1024;

    public static void main(String[] args) {
        long start = System.nanoTime();
        if (args.length > 0 && ("send".equals(args[0]) || "recv".equals(args[0]))) {
            PicocliRunner.run(Wormhole.class, args);
        } else {
            ApplicationContext context = Micronaut.run(Wormhole.class, args);
            PicocliRunner.run(Wormhole.class, context, args);
        }
        System.out.printf("Main completes: %.5fs\n", (System.nanoTime() - start) / 1_000_000_000d);
    }

    public static byte[] hash(File file) {
        try {
            byte[] chunk = new byte[DEFAULT_CHUNK_SIZE];
            MessageDigest md = MessageDigest.getInstance("MD5");
            int read;
            try (var stream = new FileInputStream(file)) {
                while ((read = stream.read(chunk)) != -1) {
                    md.update(chunk, 0, read);
                }
            }
            return md.digest();
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit((b & 0xF), 16));
        }
        return sb.toString();
    }

    @Override
    public void run() {
        System.out.println("Registrar is listening");
    }
}

package com.amazon;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.Micronaut;
import picocli.CommandLine;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@CommandLine.Command(name="wormhole", subcommands = {SenderCommand.class, ReceiverCommand.class})
public class Wormhole implements Runnable {
    public static final int DEFAULT_CHUNK_SIZE = 10 * 1024 * 1024;
    public static final int DEFAULT_THREAD_COUNT = Runtime.getRuntime().availableProcessors();
    public static final int DEFAULT_RECEIVER_PORT = 9000;

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

    static class Hasher {
        private final MessageDigest md;
        private final byte[] chunk;
        private final byte[] digest;

        public Hasher() {
            try {
                md = MessageDigest.getInstance("MD5");
                chunk = new byte[DEFAULT_CHUNK_SIZE];
                digest = new byte[16];
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }

        public final byte[] hash(File file) {
            md.reset();
            int read;
            try (var stream = new FileInputStream(file)) {
                while ((read = stream.read(chunk)) != -1) {
                    md.update(chunk, 0, read);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return md.digest();
        }
    }

    private static final ThreadLocal<Hasher> hashers = ThreadLocal.withInitial(Hasher::new);
    public static byte[] hash(File file) {
        return hashers.get().hash(file);
    }

    static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit((b & 0xF), 16));
        }
        return sb.toString();
    }

    static Path removeRoot(String filePath) {
        Path other = Paths.get(filePath);
        return other.subpath(1, other.getNameCount());
    }

    @Override
    public void run() {
        System.out.println("Registrar is listening");
    }

    static class NamingThreadFactory implements ThreadFactory {
        private final AtomicInteger count = new AtomicInteger(1);
        private final String prefix;

        NamingThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName(prefix + "-" + count.getAndIncrement());
            return t;
        }
    }
}

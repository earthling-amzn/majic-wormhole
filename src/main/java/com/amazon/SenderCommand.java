package com.amazon;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;

import static com.amazon.Wormhole.DEFAULT_CHUNK_SIZE;
import static com.amazon.Wormhole.DEFAULT_THREAD_COUNT;

@Command(name = "send", description = "...",
        mixinStandardHelpOptions = true)
public class SenderCommand implements Runnable {

    @Option(names = {"-f", "--file"}, description = "A file to transfer", required = true)
    Path fileToSend;

    @Option(names = {"-r", "--registrar"}, description = "Registrar address", defaultValue = "http://localhost:8080")
    String registrarAddress;

    @Option(names = {"-s", "--sender"}, description = "Your name, will be shown to receiver")
    String senderName;

    @Option(names = {"-e", "--receiver"}, description = "The name of a registered receiver")
    String receiverName;

    @Option(names = {"-d", "--direct"}, description = "Use direct buffers for file transfer.")
    boolean useDirect = false;

    @Option(names = {"-c", "--chunk"}, description = "Chunk size for transfer buffer in bytes")
    int chunkSize = DEFAULT_CHUNK_SIZE;

    @Option(names = {"-v", "--validate"}, description = "Send checksum of transferred file for validation")
    boolean validate;

    @Option(names = {"-t", "--threads"}, description = "Number of threads to use for sending files")
    int threadCount = DEFAULT_THREAD_COUNT;

    @Override
    @Command(name = "send")
    public void run() {
        long start = System.nanoTime();

        // Try to get the address of the receiver so we know where to send the file
        var registration = getReceiverRegistration();

        var sender = getSender();
        try {
            start = System.nanoTime();
            sender.send(fileToSend.toFile(), registration.address(), registration.port());
        } finally {
            long end = System.nanoTime();
            String message = CommandLine.Help.Ansi.AUTO.string("@|bold,green Transfer complete. |@");
            System.out.println(message);
            double elapsed = (end - start) / 1_000_000_000d;
            long transferred = sender.getBytesTransferred();
            ByteUnit bytesTransferred = ByteUnit.forBytes(transferred);
            ByteUnit transferRate = ByteUnit.forBytes((long) (transferred / elapsed));
            System.out.printf("\tTime: %.5fs.\n\tFiles: %s\n\tBytes: %s%s (%s)\n\tRate: %s%s/s\n",
                    elapsed, sender.getFilesTransferred(),
                    bytesTransferred.value, bytesTransferred.units, transferred,
                    transferRate.value, transferRate.units);
        }
    }

    record ByteUnit(long value, String units) {
        static ByteUnit forBytes(long bytes) {
            final long KB = 1024;
            if (bytes < KB) {
                return new ByteUnit(bytes, "b");
            }
            bytes /= KB;
            if (bytes < KB) {
                return new ByteUnit(bytes, "kb");
            }
            bytes /= KB;
            if (bytes < KB) {
                return new ByteUnit(bytes, "mb");
            }
            bytes /= KB;
            return new ByteUnit(bytes, "gb");
        }
    }

    private Sender getSender() {
        System.out.printf("Use NIO? %s, Validate? %s, Chunk Size: %s, Threads: %s\n",
                useDirect, validate, chunkSize, threadCount);
        return useDirect
                ? new ChannelSender(senderName, chunkSize, threadCount, validate)
                : new SimpleBlockingSender(senderName, chunkSize, threadCount, validate);
    }

    private Registration getReceiverRegistration() {
        try (var client = HttpClient.create(new URL(registrarAddress))) {
            var request = HttpRequest.GET("/fetch");
            request.getParameters().add("receiver", receiverName);
            return client.toBlocking().retrieve(request, Registration.class);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
}

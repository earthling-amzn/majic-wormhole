package com.amazon;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;

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

    @Option(names = {"-x", "--stats"}, description = "Write stats to file in CSV")
    Path statsFilePath;

    @Option(names = {"-p", "--repeat"}, description = "Repeat command N times.")
    int repeatCount = 1;

    @Override
    @Command(name = "send")
    public void run() {
        // Try to get the address of the receiver so we know where to send the file
        var registration = getReceiverRegistration();
        var sender = getSender();
        double elapsed = 0;

        for (int i = 0; i < repeatCount; ++i) {
            var start = System.nanoTime();
            sender.send(fileToSend.toFile(), registration.address(), registration.port());
            long end = System.nanoTime();

            elapsed = (end - start) / 1_000_000_000d;
            String message = CommandLine.Help.Ansi.AUTO.string("@|bold,green Transfer completed: " + elapsed + "s. |@");
            System.out.println(message);
        }

        if (statsFilePath != null) {
            writeStatsToFile(sender, elapsed);
        } else {
            printStatsToConsole(sender, elapsed);
        }
    }

    private void writeStatsToFile(Sender sender, double elapsed) {
        try (FileWriter writer = new FileWriter(statsFilePath.toFile(), true)) {
            long transferred = sender.getBytesTransferred();
            writer.write(String.valueOf(elapsed));
            writer.write(",");
            writer.write(String.valueOf(transferred));
            writer.write(",");
            writer.write(String.valueOf(sender.getFilesTransferred()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void printStatsToConsole(Sender sender, double elapsed) {
        long transferred = sender.getBytesTransferred();
        ByteUnit bytesTransferred = ByteUnit.forBytes(transferred);
        ByteUnit transferRate = ByteUnit.forBytes((long) (transferred / elapsed));
        System.out.printf("\tTime: %.5fs.\n\tFiles: %s\n\tBytes: %s%s (%s)\n\tRate: %s%s/s\n",
                elapsed, sender.getFilesTransferred(),
                bytesTransferred.value, bytesTransferred.units, transferred,
                transferRate.value, transferRate.units);
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
        var configuration = new DefaultHttpClientConfiguration();
        configuration.setNumOfThreads(1);
        configuration.setReadTimeout(Duration.ofSeconds(3));
        final var maxTries = 5;
        var tries = 0;
        while (true) {
            ++tries;
            try (var client = HttpClient.create(new URL(registrarAddress), configuration)) {
                var request = HttpRequest.GET("/fetch");
                request.getParameters().add("receiver", receiverName);
                return client.toBlocking().retrieve(request, Registration.class);
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            } catch (ReadTimeoutException e) {
                if (tries >= maxTries) {
                    throw e;
                } else {
                    System.out.println("Registration timed out, retries remaining: " + (maxTries - tries));
                }
            }
        }
    }
}

package com.amazon;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.Micronaut;
import picocli.CommandLine;

@CommandLine.Command(name="wormhole", subcommands = {SenderCommand.class, ReceiverCommand.class})
public class Wormhole implements Runnable {
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

    @Override
    public void run() {
        System.out.println("Registrar is listening");
    }
}

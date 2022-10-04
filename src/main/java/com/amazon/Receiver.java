package com.amazon;

import java.nio.file.Path;

public interface Receiver {

    void receive();

    void setTargetDirectory(Path targetDirectory);

    void setAcceptor(SimpleBlockingReceiver.Acceptor acceptor);

    void stop();

    interface Acceptor {
        boolean accept(String sender, String filename, long length);
    }
}

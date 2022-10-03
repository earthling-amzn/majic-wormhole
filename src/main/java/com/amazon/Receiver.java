package com.amazon;

import java.nio.file.Path;

public interface Receiver {
    int CHUNK_SIZE = 10 * 1024 * 1024;

    void receive();

    void setTargetDirectory(Path targetDirectory);

    void setAcceptor(SimpleBlockingReceiver.Acceptor acceptor);

    interface Acceptor {
        boolean accept(String sender, String filename, long length);
    }
}

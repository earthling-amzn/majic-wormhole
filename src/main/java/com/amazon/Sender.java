package com.amazon;

import java.io.File;

public interface Sender {
    void send(File source, String host, int port);
}

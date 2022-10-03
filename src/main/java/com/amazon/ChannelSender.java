package com.amazon;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;

public class ChannelSender {
    private final String senderName;

    public ChannelSender(String senderName) {
        this.senderName = senderName;
    }

    public void send(File source, String host, int port) {
        try (SocketChannel socket = SocketChannel.open(new InetSocketAddress(host, port));
             FileInputStream fis = new FileInputStream(source)) {
            FileChannel channel = fis.getChannel();
            channel.transferTo(0, 10, socket);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

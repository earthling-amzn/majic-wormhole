package com.amazon;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class ChannelSender implements Sender {
    private final String senderName;
    private final int chunkSize;

    public ChannelSender(String senderName, int chunkSize) {
        this.senderName = senderName;
        this.chunkSize = chunkSize;
    }

    public void send(File source, String host, int port) {
        try (SocketChannel socket = SocketChannel.open(new InetSocketAddress(host, port));
             FileInputStream fileInputStream = new FileInputStream(source)) {

            String header = senderName + ":" + source.length() + ":" + source.getName() +"\n";
            socket.write(ByteBuffer.wrap(header.getBytes(StandardCharsets.UTF_8)));

            var proceed = ByteBuffer.allocate(10);
            socket.read(proceed);
            proceed.flip();

            byte accepted = proceed.get();
            if (accepted != 1) {
                System.out.println("Cannot proceed with upload: " + accepted);
                return;
            }

            FileChannel channel = fileInputStream.getChannel();
            long readFrom = 0;
            while (true) {
                long transferred = channel.transferTo(readFrom, chunkSize, socket);
                if (transferred <= 0) {
                    break;
                }
                readFrom += transferred;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

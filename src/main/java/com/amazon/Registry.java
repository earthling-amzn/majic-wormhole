package com.amazon;

import io.micronaut.context.annotation.Bean;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Bean
public class Registry {

    Map<String, Registration> registry = new ConcurrentHashMap<>();

    public Registration create(String receiverName, String clientAddress, int port) {
        var registration = new Registration(clientAddress, port);
        var value = registry.putIfAbsent(receiverName, registration);
        return value != null ? value : registration;
    }

    public Registration get(String passcode) {
        Registration registration = registry.get(passcode);
        if (registration == null) {
            throw new IllegalArgumentException("No registration");
        }
        return registration;
    }
}

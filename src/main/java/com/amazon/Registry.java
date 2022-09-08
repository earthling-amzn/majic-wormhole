package com.amazon;

import io.micronaut.context.annotation.Bean;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Bean
public class Registry {

    Map<String, Registration> registry = new ConcurrentHashMap<>();

    public Registration create(String clientAddress, long port) {
        String passcode = getPasscode();
        var registration = new Registration(clientAddress, port, passcode);
        registry.putIfAbsent(passcode, registration);
        return registration;
    }

    public Registration get(String passcode) {
        Registration registration = registry.get(passcode);
        if (registration == null) {
            throw new IllegalArgumentException("No registration");
        }
        return registration;
    }

    private static final String[] PASSCODES = {
            "dr-rock", "sorry-charlie", "captain-fantasy", "birthday-boy"
    };

    private static String getPasscode() {
        int index = ThreadLocalRandom.current().nextInt(PASSCODES.length);
        return PASSCODES[index];
    }
}

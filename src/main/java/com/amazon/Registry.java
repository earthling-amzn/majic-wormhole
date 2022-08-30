package com.amazon;

import io.micronaut.cache.CacheManager;
import io.micronaut.cache.SyncCache;
import jakarta.inject.Inject;

import java.util.concurrent.ThreadLocalRandom;

public class Registry {
    @Inject
    CacheManager<?> caches;

    public Registration create(String clientAddress, long port) {
        String passcode = getPasscode();
        SyncCache<?> registry = caches.getCache("registry");
        var registration = new Registration(clientAddress, port, passcode);
        registry.putIfAbsent(passcode, registration);
        return registration;
    }

    public Registration get(String passcode) {
        SyncCache<?> registry = caches.getCache("registry");
        return registry.get(passcode, Registration.class).orElseThrow();
    }

    private static final String[] PASSCODES = {
            "dr-rock", "sorry-charlie", "captain-fantasy", "birthday-boy"
    };

    private static String getPasscode() {
        int index = ThreadLocalRandom.current().nextInt(PASSCODES.length);
        return PASSCODES[index];
    }
}

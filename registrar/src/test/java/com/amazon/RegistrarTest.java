package com.amazon;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
public class RegistrarTest {
    @Inject
    EmbeddedServer server;

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void testCreateRegistration() {
        var request = HttpRequest.GET("/register");
        request.getParameters().add("port", "9000");
        Registration response = client.toBlocking().retrieve(request, Registration.class);
        assertEquals("127.0.0.1", response.address());
        assertEquals(9000, response.port());
        assertNotNull(response.passcode());
    }

    @Test
    void testFetchMissingRegistration() {
        try {
            var request = HttpRequest.GET("/fetch");
            request.getParameters().add("passcode", "bad-password");
            client.toBlocking().retrieve(request, Registration.class);
        } catch (HttpClientResponseException e) {
            assertEquals(HttpStatus.NOT_FOUND, e.getResponse().getStatus());
        }
    }

    @Test
    void testFetchValidRegistration() {
        var register = HttpRequest.GET("/register");
        register.getParameters().add("port", "9000");
        Registration created = client.toBlocking().retrieve(register, Registration.class);
        var fetch = HttpRequest.GET("/fetch");
        fetch.getParameters().add("passcode", created.passcode());
        Registration fetched = client.toBlocking().retrieve(fetch, Registration.class);
        assertEquals(created, fetched);
    }
}

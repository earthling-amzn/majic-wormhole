package com.amazon;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
public class RegistrarTest {
    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void testCreateRegistration() {
        var request = HttpRequest.GET("/register");
        request.getParameters().add("username", "Dr. Rock");
        request.getParameters().add("target", "9000");
        Registration response = client.toBlocking().retrieve(request, Registration.class);
        assertEquals("127.0.0.1", response.address());
        assertEquals(9000, response.port());
    }

    @Test
    void testFetchValidRegistration() {
        var register = HttpRequest.GET("/register");
        register.getParameters().add("username", "Dr. Rock");
        register.getParameters().add("target", "9000");
        Registration created = client.toBlocking().retrieve(register, Registration.class);
        var fetch = HttpRequest.GET("/fetch");
        fetch.getParameters().add("receiver", "Dr. Rock");
        Registration fetched = client.toBlocking().retrieve(fetch, Registration.class);
        assertEquals(created, fetched);
    }
}

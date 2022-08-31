package com.amazon;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.server.util.HttpClientAddressResolver;
import jakarta.inject.Inject;

import java.util.NoSuchElementException;

@Controller("/")
public class Registrar {
    @Inject
    HttpClientAddressResolver clientAddressResolver;

    @Inject
    Registry registry;

    @Get("/register")
    public Registration register(HttpRequest<?> request, @QueryValue("port") long port) {
        String clientAddress = clientAddressResolver.resolve(request);
        return registry.create(clientAddress, port);
    }

    @Get("/fetch")
    public HttpResponse<?> fetch(@QueryValue("passcode") String passcode) {
        try {
            return HttpResponse.ok(registry.get(passcode));
        } catch (NoSuchElementException e) {
            return HttpResponse.notFound();
        }
    }
}

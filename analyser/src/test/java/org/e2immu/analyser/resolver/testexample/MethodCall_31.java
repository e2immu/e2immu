package org.e2immu.analyser.resolver.testexample;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Objects;

public class MethodCall_31 {

    private static final long DEFAULT_TIMEOUT = 30L;
    private static final String ACCEPT = "Accept";

    public static HttpRequest same4(URI uri, String a1, String a2, Long timeout) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .GET()
                .uri(uri)
                .timeout(Duration.ofMillis(Objects.requireNonNullElse(timeout, DEFAULT_TIMEOUT)))
                .header(ACCEPT, a1)
                .header("Accept", a2);
        return builder.build();
    }
}

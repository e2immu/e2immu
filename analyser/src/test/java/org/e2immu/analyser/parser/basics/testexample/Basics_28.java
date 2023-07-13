package org.e2immu.analyser.parser.basics.testexample;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;

public class Basics_28 {
    private static final long DEFAULT_TIMEOUT = 30L;
    private static final String ACCEPT = "Accept";

    public static HttpRequest same3(URI uri, Long timeout, String accept1, String accept2) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .GET()
                .uri(uri);
        // noinspection ALL
        if (timeout == null) {
            builder.timeout(Duration.ofMillis(DEFAULT_TIMEOUT));
        } else {
            builder.timeout(Duration.ofMillis(timeout));
        }
        builder.header(ACCEPT, accept1);
        builder.header("Accept", accept2);
        return builder.build();
    }
}

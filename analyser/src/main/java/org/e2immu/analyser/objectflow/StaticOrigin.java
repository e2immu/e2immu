package org.e2immu.analyser.objectflow;

import java.util.stream.Stream;

public class StaticOrigin implements Origin {
    public static final StaticOrigin LITERAL = new StaticOrigin("literal");
    public static final StaticOrigin OPERATOR = new StaticOrigin("operator");
    public static final StaticOrigin NO_ORIGIN = new StaticOrigin("<no origin>");

    private final String reason;

    StaticOrigin(String reason) {
        this.reason = reason;
    }

    @Override
    public String toString() {
        return reason;
    }

    @Override
    public Stream<ObjectFlow> sources() {
        return Stream.of();
    }
}

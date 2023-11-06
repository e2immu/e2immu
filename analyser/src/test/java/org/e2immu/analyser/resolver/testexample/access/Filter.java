package org.e2immu.analyser.resolver.testexample.access;

public interface Filter {

    Result filter(String s);

    enum Result {
        ACCEPT, NEUTRAL, DENY;
    }
}

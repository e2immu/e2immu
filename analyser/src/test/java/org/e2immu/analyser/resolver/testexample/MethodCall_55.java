package org.e2immu.analyser.resolver.testexample;

public class MethodCall_55 {

    interface D {
        long id();
    }

    String method(D d) {
        // noinspection ALL
        StringBuilder buf = new StringBuilder();
        buf.append(d.id() != Long.MIN_VALUE ? d.id() : "");
        return buf.toString();
    }
}

package org.e2immu.analyser.testexample;

public class E2Immutable_10 {

    private final Sub sub = new Sub();

    private static class Sub {
        private String string;
    }

    public String method() {
        return sub.string;
    }
}

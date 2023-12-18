package org.e2immu.analyser.resolver.testexample;

public class Constructor_18 {

    interface I {

    }

    Constructor_18(String s, I... is) {

    }

    Constructor_18(Object... objects) {

    }

    static Constructor_18 method(String s) {
        return new Constructor_18(s);
    }
}

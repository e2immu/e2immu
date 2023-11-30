package org.e2immu.analyser.parser.basics.testexample;

public class Basics_31B {
    private String c;

    public Basics_31B(String c) {
        this.c = c.toUpperCase(); // to remove null pointer warnings
    }

    public String getC() {
        return c;
    }

    public String method() {
        String res = this.c.substring(0, 10);
        this.c = res.substring(1);
        return "abc";
    }
}

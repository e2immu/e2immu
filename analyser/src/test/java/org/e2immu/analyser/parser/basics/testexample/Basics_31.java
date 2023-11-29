package org.e2immu.analyser.parser.basics.testexample;

public class Basics_31 {
    private String c;
    private final String s;

    public Basics_31(String s, String c) {
        this.c = c.toUpperCase(); // to remove null pointer warnings
        this.s = s.toLowerCase();
    }

    public String getC() {
        return c;
    }

    public String method1() {
        this.c = c.substring(1);
        return s;
    }

    public String method2() {
        this.c = c.toLowerCase().substring(1);
        return s;
    }

    public String method3() {
        this.c = s.substring(c.length());
        return s;
    }

    public String method4() {
        String res = this.c.substring(0, 10);
        this.c = res.substring(1);
        return "abc";
    }

    // this is the most complex case
    public String method5() {
        String res = this.c.substring(0, 10);
        this.c = c.substring(11);
        return res;
    }
}

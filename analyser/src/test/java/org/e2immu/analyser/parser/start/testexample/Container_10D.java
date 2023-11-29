package org.e2immu.analyser.parser.start.testexample;

public class Container_10D {
    private String c;

    public Container_10D(String cParam) {
        this.c = cParam;
    }

    public String next() {
        int i = c.indexOf("abc");
        this.c = c.substring(i + 1);
        return "abc";
    }
}

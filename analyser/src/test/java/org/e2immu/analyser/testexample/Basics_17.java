package org.e2immu.analyser.testexample;

public class Basics_17 {

    private final String string;

    public Basics_17(String string) {
        this.string = string;
        if (string == null) throw new UnsupportedOperationException();
    }

    public String string() {
        return string;
    }
}

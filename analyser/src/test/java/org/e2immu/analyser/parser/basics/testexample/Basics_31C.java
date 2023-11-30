package org.e2immu.analyser.parser.basics.testexample;

import org.e2immu.annotation.NotNull;

public class Basics_31C {
    @NotNull
    private String c;

    public Basics_31C(String c) {
        this.c = c.toUpperCase(); // to remove null pointer warnings
    }

    public String method() {
        String res = this.c.substring(0, 10);
        this.c = c.substring(11);
        return res;
    }
}


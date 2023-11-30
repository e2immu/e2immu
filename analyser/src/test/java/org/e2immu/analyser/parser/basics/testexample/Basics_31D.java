package org.e2immu.analyser.parser.basics.testexample;

import org.e2immu.annotation.NotNull;

public class Basics_31D {

    @NotNull
    private String s;

    public Basics_31D(String s) {
        this.s = s.toLowerCase();
    }

    public String method() {
        int idx = s.indexOf("abc");
        if (idx == -1) {
            return s;
        }
        String res = s.substring(0, idx);
        s = s.substring(idx + 1);
        return res;
    }
}

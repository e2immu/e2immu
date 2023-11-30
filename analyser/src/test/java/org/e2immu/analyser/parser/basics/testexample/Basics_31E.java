package org.e2immu.analyser.parser.basics.testexample;

import org.e2immu.annotation.NotNull;

import java.util.Objects;

public class Basics_31E {
    private boolean b;

    @NotNull
    private String s;

    public Basics_31E(String value) {
        this.s = Objects.requireNonNull(value);
    }

    public String method() {
        int idx = this.s.indexOf("abc");
        if (idx == -1) {
            return this.s;
        }
        String res = this.s.substring(0, idx);
        this.s = s.substring(idx + 1);
        this.b = !s.isEmpty();
        return res;
    }

    public boolean isB() {
        return b;
    }
}

package org.e2immu.analyser.parser.functional.testexample;

import java.util.ArrayList;
import java.util.List;

public class Lambda_19Recursion {

    private final List<String> list;
    private int field;

    public Lambda_19Recursion(List<String> list) {
        this.list = list;
    }

    public int recursive(int k) {
        this.field = k;
        list.subList(0, k).forEach(s -> {
            if (s.startsWith("x")) {
                recursive(k - 1);
            }
        });
        return list.size();
    }

    public int getField() {
        return field;
    }
}

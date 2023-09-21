package org.e2immu.analyser.parser.functional.testexample;

import java.util.ArrayList;
import java.util.List;

public class Lambda_19Unreachable {

    private List<String> list = new ArrayList<>(); // WARN
    private int field;

    public Lambda_19Unreachable(List<String> list) {
        this.list = list;
    }

    public int recursive(int k) {
        this.field = k;
        if (k != field) { // ERROR
            list.subList(0, k).forEach(s -> { // ERROR
                if (s.startsWith("x")) {
                    recursive(k - 1);
                }
            });
        }
        return list.size(); // WARN
    }

    public int getField() {
        return field;
    }
}

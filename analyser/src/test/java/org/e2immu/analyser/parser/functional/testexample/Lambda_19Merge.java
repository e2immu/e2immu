package org.e2immu.analyser.parser.functional.testexample;

import java.util.ArrayList;
import java.util.List;

public class Lambda_19Merge {

    private List<String> list = new ArrayList<>();
    private Integer field;

    public int recursive (int k) {
        this.field = k;
        if(k == field) {
            list.subList(0, k).forEach(s -> {
                if (s.startsWith("x")) {
                    recursive(k-1);
                }
            });
        }
        return list.size();
    }
}

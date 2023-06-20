package org.e2immu.analyser.resolver.testexample;

import java.util.HashMap;
import java.util.Map;

/*
want to ensure that == is an object equals, not an int equals.
 */
public class Basics_9 {
    private final Map<String, Integer> map = new HashMap<>();

    public int method1(String k) {
        Integer v = map.get(k);
        int r;
        if (v == null) {
            int newValue = k.length();
            map.put(k, newValue);
            r = newValue;
        } else {
            r = v;
        }
        return r;
    }
}

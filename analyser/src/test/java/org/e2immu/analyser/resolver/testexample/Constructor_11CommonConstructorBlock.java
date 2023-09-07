package org.e2immu.analyser.resolver.testexample;

import java.util.HashMap;
import java.util.Map;


public class Constructor_11CommonConstructorBlock {

    private final Map<String, Integer> map;

    {
        map = new HashMap<String, Integer>();
    }

    public Constructor_11CommonConstructorBlock(String s, String t) {
        map.put(s, 1);
        map.put(t, 2);
    }

    public Integer get(String s) {
        return map.get(s);
    }
}

package org.e2immu.analyser.parser.modification.testexample;

import org.e2immu.annotation.Modified;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Modification_28 {
    private final Map<String, List<Integer>> map = new HashMap<>();

    public void method(String key, @Modified List<Integer> value) {
        List<Integer> oldValue = map.get(key);
        List<Integer> newValue;
        if (oldValue == null) {
            newValue = value;
        } else {
            newValue = new ArrayList<>(oldValue);
            newValue.addAll(value);
        }
        map.put(key, newValue);
    }

    public Map<String, List<Integer>> getMap() {
        return map;
    }
}

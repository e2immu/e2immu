package org.e2immu.analyser.resolver.testexample;

import java.util.HashMap;
import java.util.Map;

public class Array_0 {
    Map<String, Object> map = new HashMap<>();

    public int method1() {
        Map.Entry[] entries = map.entrySet().toArray(new Map.Entry[0]);
        return entries.length;
    }

    public int method2() {
        Map.Entry<String, Object>[] entries = map.entrySet().toArray(new Map.Entry[0]);
        return entries.length;
    }
}

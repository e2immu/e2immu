package org.e2immu.analyser.testexample;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class Lambda_1 {

    public static <K, V> boolean add(Map<K, List<V>> map, K k, V v) {
        List<V> list = map.computeIfAbsent(k, l -> new LinkedList<>());
        return list.add(v);
    }

}

package org.e2immu.analyser.parser.external.testexample;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class External_13 {

    public static Map<Long, Long[]> m1(Map<Object, Object> map) {
        HashMap<Long, Long[]> result = new HashMap<>();
        for (Map.Entry<Object, Object> e : map.entrySet()) {
            Object value = e.getValue();
            Long l = m3(e.getKey());
            Long[] la = m2(value);
            result.put(l, la);
        }
        return result;
    }

    public static Long[] m2(Object o) {
        if (o instanceof List<?> list) {
            Long[] la = new Long[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Long l = m3(list.get(i));
                la[i] = l;
            }
            return la;
        }
        throw new IllegalArgumentException();
    }

    public static Long m3(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        throw new IllegalArgumentException();
    }
}

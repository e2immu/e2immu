package org.e2immu.analyser.resolver.testexample;

import java.util.Map;

public class MethodCall_37 {

    interface  DV extends Comparable<DV> {}

    static <T extends Comparable<? super T>> int compareMaps(Map<T, DV> map1, Map<T, DV> map2) {
        int differentValue = 0;
        for (Map.Entry<T, DV> e : map1.entrySet()) {
            DV dv = map2.get(e.getKey());
            if (dv == null) {
                return 0;
            }
        }
        return differentValue;
    }
}

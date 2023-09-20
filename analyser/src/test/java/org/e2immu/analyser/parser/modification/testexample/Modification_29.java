package org.e2immu.analyser.parser.modification.testexample;

import org.e2immu.annotation.Modified;

import java.util.Map;
import java.util.Set;

public class Modification_29 {

    public static <T> void modifyMap1(@Modified Map<String, T> map) {
        modifySet(map.keySet());
    }

    public static <T> void modifyMap2(@Modified Map<String, T> map) {
        Set<String> set = map.keySet();
        modifySet(set);
    }

    private static void modifySet(@Modified Set<String> set) {
        if (set.contains("abc")) {
            set.remove("def");
        }
    }
}

package org.e2immu.analyser.resolver.testexample;

import java.util.ArrayList;
import java.util.Map;

public class MethodCall_42 {
    public static final String TRUE = "1";
    public static final short True = 1;

    public static Boolean toBoolean(short bool) {
        if (bool == True) {
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    public static Boolean toBoolean(String bool) {
        if (bool == null) {
            return Boolean.FALSE;
        }
        if (bool.equalsIgnoreCase(TRUE) || bool.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    public String method(Map<String, ArrayList<String>> map) {
        return map.entrySet().stream().filter(e -> toBoolean(e.getValue().get(0)))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
}

package org.e2immu.analyser.parser.external.testexample;

import java.util.List;

public class External_8A {

    public static Long[] convertToLongArray(Object source) {
        if (source instanceof List<?>) {
            Long[] result = new Long[((List<?>) source).size()];
            for (int i = 0; i < ((List<?>) source).size(); i++) {
                long valueLong = convertToLong(((List<?>) source).get(i));
                result[i] = valueLong;
            }
            return result;
        } else if (source instanceof Object[]) {
            Long[] result = new Long[((Object[]) source).length];
            for (int i = 0; i < ((Object[]) source).length; i++) {
                Object value = ((Object[]) source)[i];
                long valueLong = convertToLong(value);
                result[i] = valueLong;
            }
            return result;
        } else {
            throw new IllegalArgumentException();
        }
    }

    public static long convertToLong(Object source) {
        if (source instanceof Number n) {
            return n.longValue();
        }
        if (source instanceof String s) {
            return Long.parseLong(s);
        }
        throw new IllegalArgumentException();
    }
}

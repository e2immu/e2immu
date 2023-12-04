package org.e2immu.analyser.parser.external.testexample;

import java.util.List;

public class External_8B {

    public static Long[] convertToLongArray(Object source) {
        if (source instanceof List<?> list) {
            Long[] result = new Long[list.size()];
            for (int i = 0; i < list.size(); i++) {
                long valueLong = convertToLong(list.get(i));
                result[i] = valueLong;
            }
            return result;
        }
        if (source instanceof Object[]) {
            Long[] result = new Long[((Object[])source).length];
            for (int i = 0; i < ((Object[])source).length; i++) {
                Object value = ((Object[])source)[i];
                long valueLong = convertToLong(value);
                result[i] = valueLong;
            }
            return result;
        }
        throw new IllegalArgumentException();
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

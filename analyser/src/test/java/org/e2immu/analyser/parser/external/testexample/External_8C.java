package org.e2immu.analyser.parser.external.testexample;

import java.util.List;

public class External_8C {

    public static Long[] convertToLongArray(Object source) {
        if (source instanceof List<?> list) {
            Long[] result = new Long[list.size()];
            for (int i = 0; i < list.size(); i++) {
                long valueLong = convertToLong(list.get(i));
                result[i] = valueLong;
            }
            return result;
        }
        if (source instanceof Object[] objects) {
            Long[] result = new Long[objects.length];
            for (int i = 0; i < objects.length; i++) {
                Object value = objects[i];
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

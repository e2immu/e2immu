package org.e2immu.analyser.resolver.testexample;

public class MethodCall_48 {
    public static class ArrayList<I> extends java.util.ArrayList<I> {
    }

    ArrayList<Long> list = new ArrayList<>();

    public static long[] toPrimitive(Long[] array) {
        return null;
    }

    public static int[] toPrimitive(Integer[] array) {
        return null;
    }

    public int method() {
        /*
        Current test problem: The erasure results of evaluating the toArray call contain no 'Long' info, and therefore,
        the two toPrimitive methods are ranked equally.
         */
        Long[] longs = new Long[list.size()];
        long[] contactIdsArray = toPrimitive(list.toArray(longs));
        return contactIdsArray.length;
    }
}

package org.e2immu.analyser.resolver.testexample;

import java.util.List;

public class MethodCall_32 {

    public interface I {
        int i();
    }

    public interface J {
        int j();
    }

    public static <T extends I> T filterByID(T t, long theID) {
        return filter(t, new long[]{theID}, null);
    }

    public static <T extends I> T filterByID(T t, long[] theIDs) {
        return filter(t, theIDs, null);
    }

    public static <T extends I> T filterByID(T t, List<Long> theIDs) {
        return filter(t, theIDs.stream().mapToLong(l -> l).toArray(), null);
    }

    public static <T extends I> T filter(T t, long[] theIDs, T target) {
        return null;
    }

    public static <T extends J> T filterByID(T t, long theID) {
        return filter(t, new long[]{theID}, null);
    }

    public static <T extends J> T filterByID(T t, long[] theIDs) {
        return filter(t, theIDs, null);
    }

    public static <T extends J> T filterByID(T t, List<Long> theIDs) {
        return filter(t, theIDs.stream().mapToLong(l -> l).toArray(), null);
    }

    public static <T extends J> T filter(T t, long[] theIDs, T target) {
        return null;
    }

    record X(int i) implements I {
    }

    X test1(X x, long id) {
        return filterByID(x, new long[]{id});
    }

    X test2(X x, long id) {
        return filterByID(x, id);
    }

    record Y(int j) implements J {
    }

    Y test3(Y y, long id) {
        return filterByID(y, new long[]{id});
    }

    Y test4(Y y, long id) {
        return filterByID(y, id);
    }
}

package org.e2immu.analyser.model;

import org.e2immu.annotation.UtilityClass;

import java.util.Arrays;
import java.util.function.IntBinaryOperator;
import java.util.stream.IntStream;

/**
 * Properties have numeric values, according to a number of different encoding systems.
 * We try to keep these systems as close together as possible, using constants to allow for some flexibility of refactoring.
 */

@UtilityClass
public class Level {

    private Level() {
        throw new UnsupportedOperationException();
    }


    // TERNARY SYSTEM
    public static final int DELAY = -1;
    public static final int UNDEFINED = -1;
    public static final int FALSE = 0;
    public static final int TRUE = 1;

    // READ, ASSIGN
    public static final int READ_ASSIGN_ONCE = TRUE;
    public static final int READ_ASSIGN_MULTIPLE_TIMES = 2;

    // SIZE SYSTEM
    public static final int SIZE_COPY_MIN_TRUE = 1;
    public static final int SIZE_COPY_TRUE = 3;

    public static final int NOT_A_SIZE = 0;
    public static final int IS_A_SIZE = 1; //  >=0
    public static final int SIZE_EMPTY = 2; // =0
    public static final int SIZE_NOT_EMPTY = 3; // >= 1


    // be careful, assumes the same level everywhere
    public static final IntBinaryOperator OR = (i, j) -> i == DELAY || j == DELAY ? DELAY : Math.max(i, j);

    /**
     * We maintain a map with overwrite protection. This function is one of such protections.
     *
     * @param from current value
     * @param to   newly proposed value
     * @return whether this increment is allowed or not
     */
    public static boolean acceptIncrement(int from, int to) {
        assert from >= DELAY;
        assert to >= DELAY;

//        if (from > to) return false; // we must go up
//        if (from == DELAY) return true; // we can always go up from delay
//        return (from % 2) == 1; // we must start from an odd value, even values are cast in stone

        return from <= to;
    }

    public static boolean better(int i, int than) {
        return i > than;
    }

    public static int best(int i, int j) {
        if (better(i, j)) return i;
        return j;
    }

    public static int fromBool(boolean b) {
        return b ? TRUE : FALSE;
    }

    public static boolean compatibleSizes(int value, int required) {
        if (haveEquals(required)) return value == required;
        return value >= required;
    }

    public static int joinSizeRestrictions(int v1, int v2) {
        if (v1 == v2) return v1;
        int min = Math.min(v1, v2);
        if (haveEquals(min)) return min - 1;
        return Math.min(v1, v2);
    }

    public static boolean haveEquals(int size) {
        if (size == Integer.MAX_VALUE) return false;
        return size >= 2 && size % 2 == 0;
    }

    public static int decodeSizeEquals(int size) {
        return size / 2 - 1;
    }

    public static int decodeSizeMin(int size) {
        return size / 2;
    }

    public static int encodeSizeEquals(int size) {
        return (1 + size) * 2;
    }

    public static int encodeSizeMin(int size) {
        return size * 2 + 1;
    }
}

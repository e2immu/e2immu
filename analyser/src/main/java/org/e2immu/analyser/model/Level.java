package org.e2immu.analyser.model;

import org.e2immu.annotation.UtilityClass;

import java.util.function.IntBinaryOperator;

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

    public static int incrementReadAssigned(int read) {
        return Math.max(Math.min(Level.READ_ASSIGN_MULTIPLE_TIMES, read + 1), Level.READ_ASSIGN_ONCE);
    }
}

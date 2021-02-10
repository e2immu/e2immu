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

    // be careful, assumes the same level everywhere
    public static final IntBinaryOperator OR = (i, j) -> i == DELAY || j == DELAY ? DELAY : Math.max(i, j);

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
}

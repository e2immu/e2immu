package org.e2immu.analyser.model;

import org.e2immu.annotation.UtilityClass;

import java.util.function.IntBinaryOperator;

/**
 * This numeric encoding aims to work efficiently with properties at multiple levels,
 * taking into account that the presence or absence of a property or level may still be in computation (delayed).
 * <p>
 * Examples are @E1Immutable, @E2Immutable (you cannot be @E2Immutable without being @E1Immutable),
 * Similarly @NotNull, @NotNull1, @NotNull2 indicates three different levels.
 * <p>
 * A value of 1 indicates that you've reached @NotNull, and are sure that
 * For that, you'd want level -2. Level 2 then indicates that
 */
@UtilityClass
public class Level {

    public static final int E1IMMUTABLE = 0;
    public static final int E2IMMUTABLE = 1;
    public static final int NOT_NULL = 0;
    public static final int NOT_NULL_1 = 1;
    public static final int NOT_NULL_2 = 2;

    private Level() {
        throw new UnsupportedOperationException();
    }

    // levels are by default 0 (also @E1Immutable), 1 for @E2Immutable, @NotNull1, 2 for @NotNull2, @Immutable

    // these are the allowed values
    public static final int DELAY = -1;
    public static final int UNDEFINED = -1;

    public static final int FALSE = 0;
    public static final int TRUE = 1;

    // be careful, assumes the same level everywhere
    public static final IntBinaryOperator AND = (i, j) -> i == DELAY || j == DELAY ? DELAY : Math.min(i, j);
    public static final IntBinaryOperator OR = (i, j) -> i == DELAY || j == DELAY ? DELAY : Math.max(i, j);

    // level == 1, then 2 = false, 3 = true, 1 = delay at this level/true at the level lower
    public static int compose(int value, int level) {
        assert value >= DELAY && value <= TRUE;
        assert level >= 0;
        return value + level * 2;
    }

    public static int value(int i, int level) {
        assert i >= DELAY;
        assert level >= 0;
        if (i <= TRUE) return i;
        int j = i / 2;
        if (j < level) return DELAY;
        if (j > level) return TRUE;
        return i % 2;
    }

    /**
     * @param i     current value
     * @param level the minimal level to be reached; must be >= 0 (levels start at 0).
     *              Note that @E1Immutable is level 0, while @NotNull1 is at level 1
     * @return true if the level reaches the threshold
     */
    public static boolean have(int i, int level) {
        assert i >= DELAY;
        assert level >= 0;
        return i >= level * 2 + 1;
    }

    public static boolean acceptIncrement(int from, int to) {
        if (from >= to) return false; // we must go up
        if (from == DELAY) return true;
        return from / 2 < to / 2; // we must go up a level
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
}

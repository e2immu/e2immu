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
 * Mixed with the different levels are delays (no information yet).
 * So very explicitly, in the case of IMMUTABLE:
 * <ul>
 *     <li>-1 means DELAY, no information about this property yet. Alternatively, we call it UNDEFINED. Because we store
 *     the values in a map, this value will replace the null value</li>
 *     <li>0 means: not E1 immutable, so also not E2 immutable; end of discussion</li>
 *     <li>1 means: E1 immutable, but we don't know about E2 immutable yet</li>
 *     <li>2 means: E1 immutable, but NOT E2 immutable; end of discussion</li>
 *     <li>3 means: E2 immutable, but we don't know about deeply immutable yet (I'm inventing a 3rd level at the moment)</li>
 *     <li>4 means: E2 immutable, but NOT deeply immutable</li>
 *     <li>5 means: deeply immutable (and there are no higher values)</li>
 * </ul>
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

    // these are the allowed values
    public static final int DELAY = -1;
    public static final int UNDEFINED = -1;

    public static final int FALSE = 0;
    public static final int TRUE = 1;
    public static final int TRUE_LEVEL_1 = 3;
    public static final int TRUE_LEVEL_2 = 5;

    // be careful, assumes the same level everywhere
    public static final IntBinaryOperator AND = (i, j) -> i == DELAY || j == DELAY ? DELAY : Math.min(i, j);
    public static final IntBinaryOperator OR = (i, j) -> i == DELAY || j == DELAY ? DELAY : Math.max(i, j);

    /**
     * make a value at a given level
     *
     * @param value the value, DELAY, TRUE or FALSE
     * @param level the level
     * @return the composite value
     */
    public static int compose(int value, int level) {
        assert value >= DELAY && value <= TRUE;
        assert level >= 0;
        return value + level * 2;
    }

    /**
     * return the value (DELAY, TRUE, FALSE) at a given level
     *
     * @param i     current value
     * @param level the level
     * @return the value restricted to that level
     */
    public static int value(int i, int level) {
        assert i >= DELAY;
        assert level >= 0;

        if (i < level * 2 + 1) return i % 2 == 0 ? FALSE : DELAY;
        return TRUE;
    }

    /**
     * @param i     current value
     * @param level the minimal level to be reached; must be >= 0 (levels start at 0).
     *              Note that @E1Immutable is level 0, while @NotNull1 is at level 1
     * @return true if the level reaches the threshold
     */
    public static boolean haveTrueAt(int i, int level) {
        assert i >= DELAY;
        assert level >= 0;
        return i >= level * 2 + 1;
    }

    /**
     * the  best level where we have TRUE involved (-1, 0 = -1 (NONE), 1, 2 = 0, 3, 4 = 1, ...)
     *
     * @param i the current value
     * @return the best level that has TRUE
     */
    public static int level(int i) {
        assert i >= DELAY;
        if (i < 1) return -1;
        return (i - 1) / 2;
    }

    /**
     * compute the value of TRUE one level higher, with a max level restriction
     *
     * @param i        the current value
     * @param maxLevel go to the next level, but not higher than this one
     * @return the new value
     */
    public static int nextLevelTrue(int i, int maxLevel) {
        assert i >= DELAY;
        assert maxLevel >= 0;
        int current = level(i);
        return compose(Level.TRUE, Math.min(current + 1, maxLevel));
    }

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
}

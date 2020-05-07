package org.e2immu.analyser.model;

import org.e2immu.annotation.UtilityClass;

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

    private Level() {
        throw new UnsupportedOperationException();
    }

    // levels are by default 0 (also @E1Immutable), 1 for @E2Immutable, @NotNull1, 2 for @NotNull2, @Immutable

    // these are the allowed values
    public static final int DELAY = 0;
    public static final int FALSE = 1;
    public static final int TRUE = 2;

    public static int tryNextLevel(int i) {
        return 3 * (1 + i / 3); // 0,1,2 to 3; 3,4,5 to 6, etc.
    }

    public static boolean decided(int i) {
        return (i % 3) != 0;
    }

    public static boolean anyDelay(int i) {
        return (i % 3) == 0;
    }

    public static int compose(int value, int level) {
        assert value >= 0 && value <= 2;
        assert level >= 0;
        return value + level * 3;
    }

    /**
     * @param i     current value
     * @param level the minimal level to be reached; must be >= 0 (levels start at 0).
     *                  Note that @E1Immutable is level 0, while @NotNull1 is at level 1
     * @return true if the level reaches the threshold
     */
    public static boolean have(int i, int level) {
        return i >= level * 3 + 2;
    }

    public static boolean acceptIncrement(int from, int to) {
        if (from >= to) return false; // we must go up
        if (to - from >= 2) return true; // any increase with 2 is fine
        // we now have an increase of 1; this is allowed, from 0 to 1, from 2 to 3, but not from 1 to 2
        return from % 3 != 1;
    }

    public static boolean better(int i, int than) {
        return i > than;
    }

    public static int best(int i, int j) {
        if (better(i, j)) return i;
        return j;
    }
}

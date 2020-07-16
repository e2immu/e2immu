package org.e2immu.analyser.model;

import org.e2immu.annotation.UtilityClass;

import java.util.Arrays;
import java.util.function.IntBinaryOperator;
import java.util.stream.IntStream;

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

    private Level() {
        throw new UnsupportedOperationException();
    }

    // these are the allowed values
    public static final int DELAY = -1;
    public static final int UNDEFINED = -1;

    public static final int FALSE = 0;
    public static final int TRUE = 1;

    public static final int EVENTUAL_BEFORE = 1;
    public static final int EVENTUAL = 2;
    public static final int EVENTUAL_AFTER = 3;
    public static final int EFFECTIVE = 4;


    /**
     * make a value at a given level
     *
     * @param valuesPerLevel a value for each level
     * @return the composite value
     */
    public static int compose(int... valuesPerLevel) {
        int result = 0;
        int multiplicator = 1;
        for (int value : valuesPerLevel) {
            result += value * multiplicator;
            multiplicator = multiplicator * 8;
        }
        return result;
    }

    public static final int MUTABLE = FALSE;
    public static final int EVENTUALLY_E1IMMUTABLE_BEFORE_MARK = compose(EVENTUAL_BEFORE);
    public static final int EVENTUALLY_E1IMMUTABLE = compose(EVENTUAL);
    public static final int EVENTUALLY_E1IMMUTABLE_AFTER_MARK = compose(EVENTUAL_AFTER);
    public static final int EFFECTIVELY_E1IMMUTABLE = compose(EFFECTIVE);

    public static final int EVENTUALLY_E1_E2IMMUTABLE_BEFORE_MARK = compose(EVENTUAL_BEFORE, EVENTUAL_BEFORE);
    public static final int EFFECTIVELY_E1_EVENTUALLY_E2IMMUTABLE_BEFORE_MARK = compose(EFFECTIVE, EVENTUAL_BEFORE);
    public static final int EVENTUALLY_E1_E2IMMUTABLE = compose(EVENTUAL, EVENTUAL);
    public static final int EFFECTIVELY_E1_EVENTUALLY_E2IMMUTABLE = compose(EFFECTIVE, EVENTUAL);
    public static final int EVENTUALLY_E2IMMUTABLE_AFTER_MARK = compose(EVENTUAL_AFTER, EVENTUAL_AFTER);
    public static final int EFFECTIVELY_E2IMMUTABLE = compose(EFFECTIVE, EFFECTIVE);

    public static final int NULLABLE = FALSE;
    public static final int EVENTUALLY_NOT_NULL_BEFORE_MARK = compose(EVENTUAL_BEFORE);
    public static final int EVENTUALLY_NOT_NULL = compose(EVENTUAL);
    public static final int EVENTUALLY_NOT_NULL_AFTER_MARK = compose(EVENTUAL_AFTER);
    public static final int EFFECTIVELY_NOT_NULL = compose(EFFECTIVE);

    public static final int EVENTUALLY_NOT_NULL_CONTENT_NOT_NULL_BEFORE_MARK = compose(EVENTUAL_BEFORE, EVENTUAL_BEFORE);
    public static final int EFFECTIVELY_NOT_NULL_EVENTUALLY_CONTENT_NOT_NULL_BEFORE_MARK = compose(EFFECTIVE, EVENTUAL_BEFORE);
    public static final int EVENTUALLY_NOT_NULL_CONTENT_NOT_NULL = compose(EVENTUAL, EVENTUAL);
    public static final int EFFECTIVELY_NOT_NULL_EVENTUALLY_CONTENT_NOT_NULL = compose(EFFECTIVE, EVENTUAL);
    public static final int EVENTUALLY_CONTENT_NOT_NULL_AFTER_MARK = compose(EVENTUAL_AFTER, EVENTUAL_AFTER);
    public static final int EFFECTIVELY_CONTENT_NOT_NULL = compose(EFFECTIVE, EFFECTIVE);

    public static final int EVENTUALLY_CONTENT2_NOT_NULL_BEFORE_MARK = compose(EVENTUAL_BEFORE, EVENTUAL_BEFORE, EVENTUAL_BEFORE);
    public static final int EFFECTIVELY_NOT_NULL_EVENTUALLY_CONTENT2_NOT_NULL_BEFORE_MARK = compose(EFFECTIVE, EVENTUAL_BEFORE, EVENTUAL_BEFORE);
    public static final int EFFECTIVELY_CONTENT_NOT_NULL_EVENTUALLY_CONTENT2_NOT_NULL_BEFORE_MARK = compose(EFFECTIVE, EFFECTIVE, EVENTUAL_BEFORE);
    public static final int EVENTUALLY_CONTENT2_NOT_NULL = compose(EVENTUAL, EVENTUAL, EVENTUAL);
    public static final int EFFECTIVELY_NOT_NULL_EVENTUALLY_CONTENT2_NOT_NULL = compose(EFFECTIVE, EVENTUAL, EVENTUAL);
    public static final int EFFECTIVELY_CONTENT_NOT_NULL_EVENTUALLY_CONTENT2_NOT_NULL = compose(EFFECTIVE, EFFECTIVE, EVENTUAL);
    public static final int EVENTUALLY_CONTENT2_NOT_NULL_AFTER_MARK = compose(EVENTUAL_AFTER, EVENTUAL_AFTER, EVENTUAL_AFTER);
    public static final int EFFECTIVELY_CONTENT2_NOT_NULL = compose(EFFECTIVE, EFFECTIVE, EFFECTIVE);

    // SIZE

    public static final int SIZE_COPY_MIN_TRUE = 1;
    public static final int SIZE_COPY_TRUE = 3;

    public static final int NOT_A_SIZE = 0;
    public static final int IS_A_SIZE = 1; //  >=0
    public static final int SIZE_EMPTY = 2; // =0
    public static final int SIZE_NOT_EMPTY = 3; // >= 1

    // READ, ASSIGN

    public static final int READ_ASSIGN_ONCE = TRUE;
    public static final int READ_ASSIGN_MULTIPLE_TIMES = 2;

    // be careful, assumes the same level everywhere
    public static final IntBinaryOperator OR = (i, j) -> i == DELAY || j == DELAY ? DELAY : Math.max(i, j);

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
        if (i == -1) return -1;
        return (i >> (3 * level)) & 0x7;
    }

    public static int level(int i) {
        assert i >= 0;
        int level = 0;
        int reduce = i;
        while (reduce > 0) {
            level++;
            reduce = reduce >> 3;
        }
        return level - 1;
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

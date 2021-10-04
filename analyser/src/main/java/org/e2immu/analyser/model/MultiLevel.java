/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.model;

/*
New encoding 20211004:
first 3 bits: false, eventual, effective
rest: level
 */
public class MultiLevel {

    public static final int SHIFT = 3;
    public static final int FACTOR = 1 << SHIFT;
    public static final int AND = FACTOR - 1;

    public static final int MAX_LEVEL = 100;

    // different values effective-eventual

    public static final int DELAY = 0;
    public static final int FALSE = 1;
    public static final int EVENTUAL = 2;
    public static final int EVENTUAL_BEFORE = 3;
    public static final int EVENTUAL_AFTER = 4;
    public static final int EFFECTIVE = 5;

    // different levels

    public static final int LEVEL_1_IMMUTABLE = 0;
    public static final int LEVEL_2_IMMUTABLE = 1;
    public static final int LEVEL_3_IMMUTABLE = 2;
    public static final int LEVEL_R_IMMUTABLE = MAX_LEVEL;

    public static final int LEVEL_1_DEPENDENT = 0;
    public static final int LEVEL_R_DEPENDENT = MAX_LEVEL;

    public static final int NOT_NULL = 0;
    public static final int NOT_NULL_1 = 1;
    public static final int NOT_NULL_2 = 2;
    public static final int NOT_NULL_3 = 3;

    // DEPENDENT (only at the first level, nothing to do with eventual)

    public static final int DEPENDENT = FALSE; // no need for more
    // dependent_1 == independent at level 1, but dependent at level 2
    public static final int DEPENDENT_1 = compose(EFFECTIVE, LEVEL_1_DEPENDENT);

    // independent == independent both at level 1 (mutable content) and level 2 (immutable content)
    public static final int INDEPENDENT = compose(EFFECTIVE, LEVEL_R_DEPENDENT);

    // IMMUTABLE

    public static final int EVENTUALLY_E2IMMUTABLE_BEFORE_MARK = compose(EVENTUAL_BEFORE, LEVEL_2_IMMUTABLE);
    public static final int EVENTUALLY_E1IMMUTABLE_BEFORE_MARK = compose(EVENTUAL_BEFORE, LEVEL_1_IMMUTABLE);

    public static final int EVENTUALLY_CONTENT_NOT_NULL = compose(EVENTUAL, NOT_NULL_1);

    public static final int EVENTUALLY_E2IMMUTABLE = compose(EVENTUAL, LEVEL_2_IMMUTABLE);
    public static final int EVENTUALLY_E1IMMUTABLE = compose(EVENTUAL, LEVEL_1_IMMUTABLE);
    public static final int EVENTUALLY_RECURSIVELY_IMMUTABLE = compose(EVENTUAL, LEVEL_R_IMMUTABLE);

    public static final int EVENTUALLY_E2IMMUTABLE_AFTER_MARK = compose(EVENTUAL_AFTER, LEVEL_2_IMMUTABLE);
    public static final int EVENTUALLY_E1IMMUTABLE_AFTER_MARK = compose(EVENTUAL_AFTER, LEVEL_1_IMMUTABLE);

    public static final int EFFECTIVELY_CONTENT2_NOT_NULL = compose(EFFECTIVE, NOT_NULL_2);
    public static final int EFFECTIVELY_CONTENT_NOT_NULL = compose(EFFECTIVE, NOT_NULL_1);
    public static final int EFFECTIVELY_NOT_NULL_AFTER = compose(EVENTUAL_AFTER, NOT_NULL);
    public static final int EFFECTIVELY_NOT_NULL = compose(EFFECTIVE, NOT_NULL);

    public static final int EFFECTIVELY_RECURSIVELY_IMMUTABLE = compose(EFFECTIVE, LEVEL_R_IMMUTABLE);
    public static final int EFFECTIVELY_E2IMMUTABLE = compose(EFFECTIVE, LEVEL_2_IMMUTABLE);
    public static final int EFFECTIVELY_E1IMMUTABLE = compose(EFFECTIVE, LEVEL_1_IMMUTABLE);
    public static final int EFFECTIVELY_E3IMMUTABLE = compose(EFFECTIVE, LEVEL_3_IMMUTABLE);

    public static final int EFFECTIVELY_E1_EVENTUALLY_E2IMMUTABLE_BEFORE_MARK = EVENTUALLY_E2IMMUTABLE_BEFORE_MARK;

    public static final int MUTABLE = FALSE;
    public static final int NULLABLE = FALSE;
    public static final int NOT_INVOLVED = DELAY;

    /**
     * Make a value combining effective and level
     *
     * @param effective a value for the first three bits
     * @param level     the level
     * @return the composite value
     */
    public static int compose(int effective, int level) {
        assert effective >= 0 && effective <= EFFECTIVE;
        assert level >= 0 && level <= MAX_LEVEL;
        assert level == 0 || effective > FALSE;
        return effective + level * FACTOR;
    }

    public static int effective(int i) {
        if (i < 0) return i;
        return i & AND;
    }

    public static int effectiveAtLevel(int i, int minLevel) {
        if (i < 0) return i;
        int level = i >> SHIFT;
        if (level < minLevel) return FALSE;
        return i & AND;
    }

    public static int level(int i) {
        if (i < 0) return i;
        return i >> SHIFT;
    }

    public static boolean isEventuallyE1Immutable(int i) {
        return i == EVENTUALLY_E1IMMUTABLE || i == EVENTUALLY_E1IMMUTABLE_BEFORE_MARK;
    }

    public static boolean isEventuallyE2Immutable(int i) {
        return i == EVENTUALLY_E2IMMUTABLE || i == EVENTUALLY_E2IMMUTABLE_BEFORE_MARK;
    }

    public static boolean isAtLeastEventuallyE2Immutable(int i) {
        return i >= EVENTUALLY_E2IMMUTABLE;
    }

    public static boolean isAtLeastEventuallyE2ImmutableAfter(int i) {
        return i >= EVENTUALLY_E2IMMUTABLE_AFTER_MARK;
    }

    private static int eventualComponent(int component, boolean conditionsMetForEventual) {
        if (component == FALSE || component == EFFECTIVE) return component;
        if (conditionsMetForEventual) return EVENTUAL_AFTER;
        if (component == EVENTUAL_AFTER) throw new UnsupportedOperationException();
        return EVENTUAL_BEFORE;
    }

    public static boolean isEffectivelyNotNull(int i) {
        return i >= EFFECTIVELY_NOT_NULL_AFTER;
    }

    public static int bestNotNull(int nn1, int nn2) {
        return Math.max(nn1, nn2);
    }

    public static int bestImmutable(int imm1, int imm2) {
        return isBetterImmutable(imm1, imm2) ? imm1 : imm2;
    }

    public static boolean isBetterImmutable(int immutableDynamic, int immutableType) {
        return immutableDynamic > immutableType;
    }

    public static int before(int level) {
        return compose(EVENTUAL_BEFORE, level);
    }

    public static int after(int level) {
        return compose(EVENTUAL_AFTER, level);
    }

    public static boolean isAfterThrowWhenNotEventual(int i) {
        if (i < 0) return false;
        int effective = effective(i);
        if (effective == EVENTUAL_BEFORE) return false;
        if (effective == EVENTUAL_AFTER || effective == EVENTUAL) return true;
        throw new UnsupportedOperationException("Not eventual");
    }

    public static boolean isBeforeThrowWhenNotEventual(int i) {
        if (i < 0) return false;
        int effective = effective(i);
        if (effective == EVENTUAL_AFTER) return false;
        if (effective == EVENTUAL_BEFORE || effective == EVENTUAL) return true;
        throw new UnsupportedOperationException("Not eventual");
    }

    public static boolean isBefore(int i) {
        if (i < 0) return false;
        int effective = effective(i);
        return effective == EVENTUAL_BEFORE || effective == EVENTUAL;
    }

    // E2Container -> E1Container; Content2 NN -> content NN
    public static int oneLevelLess(int i) {
        if (i < 0) return i;
        int level = level(i);
        if (level == 0) return i;
        int effective = effective(i);
        int newLevel = level == MAX_LEVEL ? level : level - 1;
        return compose(effective, newLevel);
    }

    public static String niceIndependent(int i) {
        if (DEPENDENT == i) return "@Dependent";
        if (INDEPENDENT == i) return "@Independent";
        return "@Dependent" + (level(i) + 1);
    }
}

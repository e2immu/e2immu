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

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.LinkedVariables;

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
    public static final DV FALSE_DV = new DV.NoDelay(FALSE, "false");
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
    public static final DV DEPENDENT_DV = new DV.NoDelay(DEPENDENT, "dependent");

    // dependent_1 == independent at level 1, but dependent at level 2
    public static final int INDEPENDENT_1 = compose(EFFECTIVE, LEVEL_1_DEPENDENT);
    public static final DV INDEPENDENT_1_DV = new DV.NoDelay(INDEPENDENT_1, "independent1");

    // independent == independent both at level 1 (mutable content) and level 2 (immutable content)
    public static final int INDEPENDENT = compose(EFFECTIVE, LEVEL_R_DEPENDENT);
    public static final DV INDEPENDENT_DV = new DV.NoDelay(INDEPENDENT, "independent");

    // IMMUTABLE

    public static final DV EVENTUALLY_E2IMMUTABLE_BEFORE_MARK_DV =
            new DV.NoDelay(compose(EVENTUAL_BEFORE, LEVEL_2_IMMUTABLE), "eve2_before_mark");
    public static final DV EVENTUALLY_E1IMMUTABLE_BEFORE_MARK_DV =
            new DV.NoDelay(compose(EVENTUAL_BEFORE, LEVEL_1_IMMUTABLE), "eve1_before_mark");
    public static final int EVENTUALLY_CONTENT_NOT_NULL = compose(EVENTUAL, NOT_NULL_1);

    public static final DV EVENTUALLY_E2IMMUTABLE_DV =
            new DV.NoDelay(compose(EVENTUAL, LEVEL_2_IMMUTABLE), "eve2immutable");

    public static final DV EVENTUALLY_E1IMMUTABLE_DV =
            new DV.NoDelay(compose(EVENTUAL, LEVEL_1_IMMUTABLE), "eve1immutable");

    public static final DV EVENTUALLY_RECURSIVELY_IMMUTABLE_DV = new DV.NoDelay(compose(EVENTUAL, LEVEL_R_IMMUTABLE));


    public static final DV EVENTUALLY_E2IMMUTABLE_AFTER_MARK_DV =
            new DV.NoDelay(compose(EVENTUAL_AFTER, LEVEL_2_IMMUTABLE));
    public static final DV EVENTUALLY_E1IMMUTABLE_AFTER_MARK_DV = new DV.NoDelay(compose(EVENTUAL_AFTER, LEVEL_1_IMMUTABLE));

    public static final DV EFFECTIVELY_CONTENT2_NOT_NULL_DV =
            new DV.NoDelay(compose(EFFECTIVE, NOT_NULL_2), "content2_not_null");
    public static final DV EFFECTIVELY_CONTENT_NOT_NULL_DV =
            new DV.NoDelay(compose(EFFECTIVE, NOT_NULL_1), "content_not_null");
    public static final int EFFECTIVELY_NOT_NULL_AFTER = compose(EVENTUAL_AFTER, NOT_NULL);
    public static final DV EFFECTIVELY_NOT_NULL_DV = new DV.NoDelay(compose(EFFECTIVE, NOT_NULL), "not_null");

    public static final int EFFECTIVELY_RECURSIVELY_IMMUTABLE = compose(EFFECTIVE, LEVEL_R_IMMUTABLE);
    public static final DV EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV = new DV.NoDelay(EFFECTIVELY_RECURSIVELY_IMMUTABLE, "recursively_immutable");

    public static final int EFFECTIVELY_E2IMMUTABLE = compose(EFFECTIVE, LEVEL_2_IMMUTABLE);
    public static final DV EFFECTIVELY_E2IMMUTABLE_DV = new DV.NoDelay(EFFECTIVELY_E2IMMUTABLE, "e2immutable");
    public static final int EFFECTIVELY_E1IMMUTABLE = compose(EFFECTIVE, LEVEL_1_IMMUTABLE);
    public static final DV EFFECTIVELY_E1IMMUTABLE_DV = new DV.NoDelay(EFFECTIVELY_E1IMMUTABLE, "e1immutable");
    public static final DV EFFECTIVELY_E3IMMUTABLE_DV = new DV.NoDelay(compose(EFFECTIVE, LEVEL_3_IMMUTABLE));

    public static final int EFFECTIVELY_E1_EVENTUALLY_E2IMMUTABLE_BEFORE_MARK = compose(EVENTUAL_BEFORE, LEVEL_2_IMMUTABLE);

    public static final int MUTABLE = FALSE;
    public static final DV MUTABLE_DV = new DV.NoDelay(MUTABLE, "mutable");
    public static final int NULLABLE = FALSE;
    public static final DV NULLABLE_DV = new DV.NoDelay(NULLABLE, "nullable");
    public static final int NOT_INVOLVED = DELAY;
    public static final DV NOT_INVOLVED_DV = new DV.NoDelay(NOT_INVOLVED, "not_involved");

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

    public static int effective(DV dv) {
        return effective(dv.value());
    }

    public static int effective(int i) {
        if (i < 0) return i;
        return i & AND;
    }

    public static int effectiveAtLevel(DV dv, int minLevel) {
        return effectiveAtLevel(dv.value(), minLevel);
    }

    public static int effectiveAtLevel(int i, int minLevel) {
        if (i < 0) return i;
        int level = i >> SHIFT;
        if (level < minLevel) return FALSE;
        return i & AND;
    }

    public static int level(DV dv) {
        return level(dv.value());
    }

    public static int level(int i) {
        if (i < 0) return i;
        return i >> SHIFT;
    }

    public static boolean isEventuallyE1Immutable(int i) {
        return i == compose(EVENTUAL, LEVEL_1_IMMUTABLE) || i == compose(EVENTUAL_BEFORE, LEVEL_1_IMMUTABLE);
    }

    public static boolean isEventuallyE2Immutable(int i) {
        return i == compose(EVENTUAL, LEVEL_2_IMMUTABLE) || i == compose(EVENTUAL_BEFORE, LEVEL_2_IMMUTABLE);
    }

    public static boolean isAtLeastEventuallyE2Immutable(int i) {
        return i >= compose(EVENTUAL, LEVEL_2_IMMUTABLE);
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

    public static DV beforeDv(int level) {
        return new DV.NoDelay(compose(EVENTUAL_BEFORE, level));
    }

    public static DV afterDv(int level) {
        return new DV.NoDelay(compose(EVENTUAL_AFTER, level));
    }

    public static int after(int level) {
        return compose(EVENTUAL_AFTER, level);
    }

    public static boolean isAfterThrowWhenNotEventual(DV dv) {
        int i = dv.value();
        if (i < 0) return false;
        int effective = effective(i);
        return effective == EVENTUAL_AFTER || effective == EVENTUAL;
    }

    public static boolean isBeforeThrowWhenNotEventual(DV dv) {
        int i = dv.value();
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

    public static DV composeOneLevelLess(DV dv) {
        int i = dv.value();
        if (i < 0) return dv;
        int level = level(i);
        if (level == 0) return FALSE_DV;
        int effective = effective(i);
        int newLevel = level == MAX_LEVEL ? level : level - 1;
        return new DV.NoDelay(compose(effective, newLevel));
    }

    public static DV composeOneLevelMore(DV dv) {
        return new DV.NoDelay(composeOneLevelMore(dv.value()));
    }

    public static int composeOneLevelMore(int i) {
        if (i < 0) return i;
        int level = level(i);
        int effective = effective(i);
        int newLevel = level == MAX_LEVEL ? level : level + 1;
        return compose(effective, newLevel);
    }


    public static int oneLevelMoreFromValue(int i) {
        if (i < 0) return i;
        int level = level(i);
        return level == MAX_LEVEL ? MAX_LEVEL : level + 1;
    }

    public static String niceIndependent(DV dv) {
        if (dv instanceof DV.NoDelay noDelay && noDelay.haveLabel()) {
            return noDelay.label();
        }
        return niceIndependent(dv.value());
    }

    public static String niceIndependent(int i) {
        if (DEPENDENT == i) return "@Dependent";
        if (INDEPENDENT == i) return "@Independent";
        return "@Dependent" + (level(i) + 1);
    }

    public static String niceImmutable(DV dv) {
        if (dv instanceof DV.NoDelay noDelay && noDelay.haveLabel()) {
            return noDelay.label();
        }
        return niceImmutable(dv.value());
    }

    public static String niceImmutable(int i) {
        if (MUTABLE == i) return "@Mutable";
        int level = level(i) + 1;
        int effective = effective(i);
        String immutable = level == MultiLevel.MAX_LEVEL ? "@ERImmutable" : "@E" + level + "Immutable";
        return niceEffective(effective) + " " + immutable;
    }

    public static String niceEffective(int e) {
        return switch (e) {
            case EVENTUAL_BEFORE -> "before";
            case EVENTUAL_AFTER -> "after";
            case EVENTUAL -> "eventually";
            case EFFECTIVE -> "effectively";
            default -> "" + e;
        };
    }

    // ImmutableSet<T>. If T is E2, then combination is E3
    // ImmutableSet<Integer> -> MAX
    public static int sumImmutableLevels(int base, int parameters) {
        int levelBase = level(base);
        int levelParams = level(parameters);
        if (levelBase == MAX_LEVEL || levelParams == MAX_LEVEL) return compose(effective(base), MAX_LEVEL);
        return compose(effective(base), levelBase + levelParams);
    }

    public static DV sumImmutableLevels(DV base, DV parameters) {
        int v = base.value();
        int levelBase = level(v);
        int levelParams = level(parameters.value());
        if (levelBase == MAX_LEVEL || levelParams == MAX_LEVEL)
            return new DV.NoDelay(compose(effective(v), MAX_LEVEL));
        return new DV.NoDelay(compose(effective(v), levelBase + levelParams));
    }

    public static DV independentCorrespondingToImmutableLevelDv(int immutableLevel) {
        return new DV.NoDelay(independentCorrespondingToImmutableLevel(immutableLevel));
    }

    public static int independentCorrespondingToImmutableLevel(int immutableLevel) {
        if (immutableLevel < 0) return immutableLevel;
        if (immutableLevel == 0) return 0;
        int level;
        if (immutableLevel == MAX_LEVEL) {
            level = immutableLevel;
        } else {
            level = immutableLevel - 1;
        }
        return compose(EFFECTIVE, level);
    }

    public static boolean independentConsistentWithImmutable(int independent, int immutable) {
        assert independent >= 0;
        assert immutable >= 0;
        int levelIndependent = MultiLevel.level(independent);
        int levelImmutable = MultiLevel.level(immutable);
        if (levelImmutable == 0) return true; // @E1, mutable; independent can be anything
        return levelImmutable == levelIndependent;
    }

    public static DV fromIndependentToLinkedVariableLevel(DV dv) {
        assert dv.lt(MultiLevel.INDEPENDENT_DV); // cannot be linked
        if (dv.equals(MultiLevel.DEPENDENT_DV)) return LinkedVariables.DEPENDENT_DV;
        if (dv.equals(MultiLevel.INDEPENDENT_1_DV)) return LinkedVariables.INDEPENDENT1_DV;
        return new DV.NoDelay(level(dv) + 2);
    }

    public static boolean isAtLeastEffectivelyE2Immutable(DV dv) {
        return isAtLeastEffectivelyE2Immutable(dv.value());
    }

    public static boolean isAtLeastEffectivelyE2Immutable(int i) {
        int level = level(i);
        if (level < MultiLevel.LEVEL_2_IMMUTABLE) return false;
        int effective = effective(i);
        return effective >= MultiLevel.EVENTUAL_AFTER;
    }
}

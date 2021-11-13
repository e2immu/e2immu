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

import static org.e2immu.analyser.model.MultiLevel.Effective.*;
import static org.e2immu.analyser.model.MultiLevel.Level.*;

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

    public static Effective effectiveAtLevel(DV dv, Level target) {
        int level = MultiLevel.level(dv);
        if (level < target.level) return FALSE;
        return level > target.level ? MultiLevel.Effective.EFFECTIVE : MultiLevel.effective(dv);
    }

    public static int oneLevelMoreFrom(DV dv) {
        int level = level(dv);
        return level == MAX_LEVEL ? MAX_LEVEL : level + 1;
    }

    public enum Effective {
        DELAY(0, "delay"),
        FALSE(1, "false"),
        EVENTUAL(2, "eventual"),
        EVENTUAL_BEFORE(3, "eventual-before"),
        EVENTUAL_AFTER(4, "eventual-after"),
        EFFECTIVE(5, "effective");

        public final int value;
        public final String label;

        Effective(int value, String label) {
            this.value = value;
            this.label = label;
        }

        public static Effective of(int i) {
            return switch (i) {
                case 0 -> DELAY;
                case 1 -> FALSE;
                case 2 -> EVENTUAL;
                case 3 -> EVENTUAL_BEFORE;
                case 4 -> EVENTUAL_AFTER;
                case 5 -> EFFECTIVE;
                default -> throw new UnsupportedOperationException();
            };
        }

        public boolean ge(Effective other) {
            return value >= other.value;
        }

        public boolean lt(Effective other) {
            return value < other.value;
        }
    }
    // different values effective-eventual

    public enum Level {
        ABSENT(-1),
        BASE(0),
        IMMUTABLE_1(0), IMMUTABLE_2(1), IMMUTABLE_3(2), IMMUTABLE_R(MAX_LEVEL),
        INDEPENDENT_1(0), INDEPENDENT_2(1), INDEPENDENT_R(MAX_LEVEL),
        NOT_NULL(0), NOT_NULL_1(1), NOT_NULL_2(2), NOT_NULL_3(3);

        public final int level;

        Level(int level) {
            this.level = level;
        }

        public Level max(Level other) {
            return other.level > level ? other : this;
        }
    }
    // different levels

    // DEPENDENT (only at the first level, nothing to do with eventual)

    public static final DV DEPENDENT_DV = compose(Effective.FALSE, Level.INDEPENDENT_1, "dependent");
    public static final DV INDEPENDENT_1_DV = compose(EFFECTIVE, Level.INDEPENDENT_1, "independent1");
    public static final DV INDEPENDENT_2_DV = compose(EFFECTIVE, Level.INDEPENDENT_2, "independent2");
    public static final DV INDEPENDENT_DV = compose(EFFECTIVE, Level.INDEPENDENT_R, "independent");

    // IMMUTABLE
    public static final DV EVENTUALLY_E1IMMUTABLE_BEFORE_MARK_DV =
            compose(Effective.EVENTUAL_BEFORE, Level.IMMUTABLE_1, "eve1_before_mark");
    public static final DV EVENTUALLY_E2IMMUTABLE_BEFORE_MARK_DV =
            compose(Effective.EVENTUAL_BEFORE, Level.IMMUTABLE_2, "eve2_before_mark");
    public static final DV EVENTUALLY_ERIMMUTABLE_BEFORE_MARK_DV =
            compose(Effective.EVENTUAL_BEFORE, Level.IMMUTABLE_R, "everec_before_mark");

    public static final DV EVENTUALLY_E1IMMUTABLE_DV = compose(EVENTUAL, Level.IMMUTABLE_1, "eve1immutable");
    public static final DV EVENTUALLY_E2IMMUTABLE_DV = compose(EVENTUAL, Level.IMMUTABLE_2, "eve2immutable");
    public static final DV EVENTUALLY_RECURSIVELY_IMMUTABLE_DV = compose(EVENTUAL, Level.IMMUTABLE_R, "evrecimmutable");

    public static final DV EVENTUALLY_E1IMMUTABLE_AFTER_MARK_DV = compose(EVENTUAL_AFTER, Level.IMMUTABLE_1, "eve1immutable_after");
    public static final DV EVENTUALLY_E2IMMUTABLE_AFTER_MARK_DV = compose(EVENTUAL_AFTER, Level.IMMUTABLE_2, "eve2immutable_after");
    public static final DV EVENTUALLY_ERIMMUTABLE_AFTER_MARK_DV = compose(EVENTUAL_AFTER, Level.IMMUTABLE_R, "everecimmutable_after");

    public static final DV EFFECTIVELY_CONTENT2_NOT_NULL_DV = compose(EFFECTIVE, NOT_NULL_2, "content2_not_null");
    public static final DV EFFECTIVELY_CONTENT_NOT_NULL_DV = compose(EFFECTIVE, NOT_NULL_1, "content_not_null");
    public static final DV EFFECTIVELY_NOT_NULL_AFTER_DV = compose(EVENTUAL_AFTER, NOT_NULL, "not_null_after");
    public static final DV EFFECTIVELY_NOT_NULL_DV = compose(EFFECTIVE, NOT_NULL, "not_null");

    public static final DV EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV = compose(EFFECTIVE, IMMUTABLE_R, "recursively_immutable");

    public static final DV EFFECTIVELY_E1IMMUTABLE_DV = compose(EFFECTIVE, IMMUTABLE_1, "e1immutable");
    public static final DV EFFECTIVELY_E2IMMUTABLE_DV = compose(EFFECTIVE, Level.IMMUTABLE_2, "e2immutable");
    public static final DV EFFECTIVELY_E3IMMUTABLE_DV = compose(EFFECTIVE, IMMUTABLE_3, "e3immutable");

    public static final DV MUTABLE_DV = compose(FALSE, IMMUTABLE_1, "mutable");
    public static final DV NULLABLE_DV = compose(FALSE, NOT_NULL, "nullable");
    public static final DV NOT_INVOLVED_DV = compose(Effective.DELAY, BASE, "not_involved");

    /**
     * Make a value combining effective and level
     *
     * @param effective a value for the first three bits
     * @param level     the level
     * @return the composite value
     */
    private static DV compose(Effective effective, Level level, String label) {
        return new DV.NoDelay(effective.value + level.level * FACTOR, label);
    }

    public static DV composeIndependent(Effective effective, Level level) {
        return composeIndependent(effective, level.level);
    }

    public static DV composeIndependent(Effective effective, int level) {
        assert effective == EFFECTIVE;
        if (level == INDEPENDENT_1.level) return INDEPENDENT_1_DV;
        if (level == INDEPENDENT_2.level) return INDEPENDENT_2_DV;
        if (level == INDEPENDENT_R.level) return INDEPENDENT_DV;
        return new DV.NoDelay(EFFECTIVE.value + level * FACTOR, "independent_" + (level + 1));
    }

    public static DV composeImmutable(Effective effective, int level) {
        if (effective == EVENTUAL_BEFORE) return beforeImmutableDv(level);
        if (effective == EVENTUAL_AFTER) return afterImmutableDv(level);
        if (effective == EFFECTIVE) return effectivelyImmutable(level);
        return new DV.NoDelay(effective.value + level * FACTOR, effective.label + "_immutable" + (level + 1));
    }

    public static Effective effective(DV dv) {
        return Effective.of(dv.value() & AND);
    }

    public static int level(DV dv) {
        return dv.value() >> SHIFT;
    }

    public static boolean isEventuallyE1Immutable(DV dv) {
        return dv.equals(EVENTUALLY_E1IMMUTABLE_DV) || dv.equals(EVENTUALLY_E1IMMUTABLE_BEFORE_MARK_DV);
    }

    public static boolean isEventuallyE2Immutable(DV dv) {
        return dv.equals(EVENTUALLY_E2IMMUTABLE_DV) || dv.equals(EVENTUALLY_E2IMMUTABLE_BEFORE_MARK_DV);
    }

    public static boolean isAtLeastEventuallyE2Immutable(DV dv) {
        return dv.ge(EVENTUALLY_E2IMMUTABLE_DV);
    }

    public static boolean isAtLeastEffectivelyE2Immutable(DV dv) {
        return dv.ge(EFFECTIVELY_E2IMMUTABLE_DV);
    }

    public static boolean isEffectivelyNotNull(DV dv) {
        return dv.ge(EFFECTIVELY_NOT_NULL_AFTER_DV);
    }

    public static boolean isAtLeastE2Immutable(DV dv) {
        return dv.ge(EVENTUALLY_E2IMMUTABLE_BEFORE_MARK_DV);
    }

    public static DV effectivelyImmutable(int level) {
        if (level == IMMUTABLE_1.level) return EFFECTIVELY_E1IMMUTABLE_DV;
        if (level == IMMUTABLE_2.level) return EFFECTIVELY_E2IMMUTABLE_DV;
        if (level == IMMUTABLE_R.level) return EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV;
        return new DV.NoDelay(EFFECTIVE.value + level * FACTOR);
    }

    public static DV beforeImmutableDv(int level) {
        if (level == IMMUTABLE_1.level) return MultiLevel.EVENTUALLY_E1IMMUTABLE_BEFORE_MARK_DV;
        if (level == IMMUTABLE_2.level) return MultiLevel.EVENTUALLY_E2IMMUTABLE_BEFORE_MARK_DV;
        if (level == IMMUTABLE_R.level) return MultiLevel.EVENTUALLY_ERIMMUTABLE_BEFORE_MARK_DV;
        return new DV.NoDelay(EVENTUAL_BEFORE.value + level * FACTOR);
    }

    public static DV afterImmutableDv(int level) {
        if (level == IMMUTABLE_1.level) return MultiLevel.EVENTUALLY_E1IMMUTABLE_AFTER_MARK_DV;
        if (level == IMMUTABLE_2.level) return MultiLevel.EVENTUALLY_E2IMMUTABLE_AFTER_MARK_DV;
        if (level == IMMUTABLE_R.level) return MultiLevel.EVENTUALLY_ERIMMUTABLE_AFTER_MARK_DV;
        return new DV.NoDelay(EVENTUAL_AFTER.value + level * FACTOR);
    }

    public static boolean isAfterThrowWhenNotEventual(DV dv) {
        if (dv.isDelayed()) return false;
        Effective effective = effective(dv);
        //if (effective == EVENTUAL_BEFORE) throw new UnsupportedOperationException();
        return effective == EVENTUAL_AFTER || effective == EVENTUAL;
    }

    public static boolean isBeforeThrowWhenNotEventual(DV dv) {
        int i = dv.value();
        if (i < 0) return false;
        Effective effective = effective(dv);
        if (effective == EVENTUAL_AFTER) return false;
        if (effective == EVENTUAL_BEFORE || effective == EVENTUAL) return true;
        throw new UnsupportedOperationException("Not eventual");
    }

    public static boolean isBefore(DV dv) {
        if (dv.isDelayed()) return false;
        Effective effective = effective(dv);
        return effective == EVENTUAL_BEFORE || effective == EVENTUAL;
    }

    public static DV composeOneLevelLessIndependent(DV dv) {
        if (dv.isDelayed()) return dv;
        int level = level(dv);
        if (level == 0) return dv;
        Effective effective = effective(dv);
        int newLevel = level == MAX_LEVEL ? level : level - 1;
        return composeIndependent(effective, newLevel);
    }

    public static DV composeOneLevelLessNotNull(DV dv) {
        if (dv.isDelayed()) return dv;
        int level = level(dv);
        if (level == 0) return dv;
        int newLevel = level == MAX_LEVEL ? level : level - 1;
        return composeNotNull(newLevel);
    }

    private static DV composeNotNull(int level) {
        if (level == NOT_NULL.level) return EFFECTIVELY_NOT_NULL_DV;
        if (level == NOT_NULL_1.level) return EFFECTIVELY_CONTENT_NOT_NULL_DV;
        if (level == NOT_NULL_2.level) return EFFECTIVELY_CONTENT2_NOT_NULL_DV;
        return new DV.NoDelay(EFFECTIVE.value + level * FACTOR, "not_null_" + level);
    }

    public static DV composeOneLevelMoreNotNull(DV dv) {
        if (dv.isDelayed()) return dv;
        int level = level(dv);
        int newLevel = level == MAX_LEVEL ? level : level + 1;
        return composeNotNull(newLevel);
    }

    // ImmutableSet<T>. If T is E2, then combination is E3
    // ImmutableSet<Integer> -> MAX
    public static DV sumImmutableLevels(DV base, DV parameters) {
        int levelBase = level(base);
        int levelParams = level(parameters);
        if (levelBase == MAX_LEVEL || levelParams == MAX_LEVEL) return composeImmutable(effective(base), MAX_LEVEL);
        return composeImmutable(effective(base), levelBase + levelParams);
    }

    public static DV independentCorrespondingToImmutableLevelDv(int immutableLevel) {
        if (immutableLevel == 0) return DEPENDENT_DV;
        assert immutableLevel > 0;
        int level;
        if (immutableLevel == MAX_LEVEL) {
            level = immutableLevel;
        } else {
            level = immutableLevel - 1;
        }
        return composeIndependent(EFFECTIVE, level);
    }

    public static boolean independentConsistentWithImmutable(DV independent, DV immutable) {
        assert independent.isDone();
        assert immutable.isDone();
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
}

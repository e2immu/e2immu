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
import org.e2immu.analyser.analyser.delay.Inconclusive;
import org.e2immu.analyser.analyser.delay.NoDelay;

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
                default -> throw new UnsupportedOperationException("Value " + i);
            };
        }

        public boolean ge(Effective other) {
            return value >= other.value;
        }

        public boolean lt(Effective other) {
            return value < other.value;
        }
    }

    public enum Level {
        ABSENT(-1),
        BASE(0),
        MUTABLE(0), IMMUTABLE_HC(1), IMMUTABLE(2),
        INDEPENDENT_HC(0), INDEPENDENT(2),
        NOT_NULL(0), NOT_NULL_1(1),
        CONTAINER(0),
        IGNORE_MODS(0);

        public final int level;

        Level(int level) {
            this.level = level;
        }

        public Level max(Level other) {
            return other.level > level ? other : this;
        }
    }

    // CONTAINER (only at first level, for now not eventual; but it needs NOT_INVOLVED next to TRUE and FALSE)
    public static final DV NOT_CONTAINER_DV = compose(FALSE, CONTAINER, "not_container");
    public static final DV NOT_CONTAINER_INCONCLUSIVE = new Inconclusive(NOT_CONTAINER_DV);
    public static final DV CONTAINER_DV = compose(EFFECTIVE, CONTAINER, "container");

    // IGNORE_MODS/modifications (only at first level, for now not eventual; but it needs NOT_INVOLVED next to TRUE and FALSE)
    public static final DV NOT_IGNORE_MODS_DV = compose(FALSE, IGNORE_MODS, "not_ignore_mods");
    public static final DV IGNORE_MODS_DV = compose(EFFECTIVE, IGNORE_MODS, "ignore_mods");


    // DEPENDENT (only at the first level, for now not eventual)

    public static final DV DEPENDENT_DV = compose(Effective.FALSE, Level.INDEPENDENT_HC, "dependent");
    public static final DV DEPENDENT_INCONCLUSIVE = new Inconclusive(DEPENDENT_DV);
    public static final DV INDEPENDENT_HC_DV = compose(EFFECTIVE, Level.INDEPENDENT_HC, "independent_hc");
    public static final DV INDEPENDENT_HC_INCONCLUSIVE = new Inconclusive(INDEPENDENT_HC_DV);
    public static final DV INDEPENDENT_DV = compose(EFFECTIVE, Level.INDEPENDENT, "independent");

    // IMMUTABLE
    public static final DV EVENTUALLY_FINAL_FIELDS_BEFORE_MARK_DV =
            compose(Effective.EVENTUAL_BEFORE, Level.MUTABLE, "eve_final_fields_before_mark");
    public static final DV EVENTUALLY_IMMUTABLE_HC_BEFORE_MARK_DV =
            compose(Effective.EVENTUAL_BEFORE, Level.IMMUTABLE_HC, "eve_immutable_hc_before_mark");
    public static final DV EVENTUALLY_IMMUTABLE_BEFORE_MARK_DV =
            compose(Effective.EVENTUAL_BEFORE, Level.IMMUTABLE, "eve_immutable_before_mark");

    public static final DV EVENTUALLY_FINAL_FIELDS_DV = compose(EVENTUAL, Level.MUTABLE, "eve_final_fields");
    public static final DV EVENTUALLY_IMMUTABLE_HC_DV = compose(EVENTUAL, Level.IMMUTABLE_HC, "eve_immutable_hc");
    public static final DV EVENTUALLY_IMMUTABLE_DV = compose(EVENTUAL, Level.IMMUTABLE, "eve_immutable");

    public static final DV EVENTUALLY_FINAL_FIELDS_AFTER_MARK_DV = compose(EVENTUAL_AFTER, Level.MUTABLE, "final_fields_after");
    public static final DV EVENTUALLY_IMMUTABLE_HC_AFTER_MARK_DV = compose(EVENTUAL_AFTER, Level.IMMUTABLE_HC, "immutable_hc_after");
    public static final DV EVENTUALLY_IMMUTABLE_AFTER_MARK_DV = compose(EVENTUAL_AFTER, Level.IMMUTABLE, "eve_immutable_after");

    public static final DV EFFECTIVELY_CONTENT_NOT_NULL_DV = compose(EFFECTIVE, NOT_NULL_1, "content_not_null");
    public static final DV EFFECTIVELY_NOT_NULL_AFTER_DV = compose(EVENTUAL_AFTER, NOT_NULL, "not_null_after");
    public static final DV EFFECTIVELY_NOT_NULL_DV = compose(EFFECTIVE, NOT_NULL, "not_null");

    public static final DV EFFECTIVELY_FINAL_FIELDS_DV = compose(EFFECTIVE, MUTABLE, "final_fields");
    public static final DV EFFECTIVELY_IMMUTABLE_HC_DV = compose(EFFECTIVE, Level.IMMUTABLE_HC, "immutable_hc");
    public static final DV EFFECTIVELY_IMMUTABLE_DV = compose(EFFECTIVE, IMMUTABLE, "immutable");

    public static final DV MUTABLE_DV = compose(FALSE, MUTABLE, "mutable");
    public static final DV MUTABLE_INCONCLUSIVE = new Inconclusive(MUTABLE_DV);
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
        return new NoDelay(effective.value + level.level * FACTOR, label);
    }

    public static DV composeImmutable(Effective effective, int level) {
        return switch (effective) {
            case EVENTUAL_BEFORE -> beforeImmutableDv(level);
            case EVENTUAL_AFTER -> afterImmutableDv(level);
            case EFFECTIVE -> effectivelyImmutable(effective, level);
            case EVENTUAL -> eventuallyImmutable(level);
            case FALSE -> MultiLevel.MUTABLE_DV;
            case DELAY -> MultiLevel.NOT_INVOLVED_DV;
        };
    }

    public static Effective effective(DV dv) {
        return Effective.of(dv.value() & AND);
    }

    public static int level(DV dv) {
        return dv.value() >> SHIFT;
    }

    public static boolean isEventuallyFinalFields(DV dv) {
        return dv.equals(EVENTUALLY_FINAL_FIELDS_DV) || dv.equals(EVENTUALLY_FINAL_FIELDS_BEFORE_MARK_DV);
    }

    public static boolean isEventuallyImmutableHC(DV dv) {
        return dv.equals(EVENTUALLY_IMMUTABLE_HC_DV) || dv.equals(EVENTUALLY_IMMUTABLE_HC_BEFORE_MARK_DV);
    }

    public static boolean isAtLeastEventuallyImmutableHC(DV dv) {
        return dv.ge(EVENTUALLY_IMMUTABLE_HC_DV);
    }

    public static boolean isAtLeastEffectivelyImmutableHC(DV dv) {
        if (dv.ge(EFFECTIVELY_IMMUTABLE_HC_DV)) {
            Effective effective = effective(dv);
            return effective == EFFECTIVE;
        }
        return false;
    }

    public static boolean isEffectivelyNotNull(DV dv) {
        return dv.ge(EFFECTIVELY_NOT_NULL_AFTER_DV);
    }

    public static boolean isAtLeastImmutableHC(DV dv) {
        return dv.ge(EVENTUALLY_IMMUTABLE_HC_DV);
    }

    public static DV effectivelyImmutable(Effective effective, int level) {
        return switch (level) {
            case 0 -> effective == Effective.EFFECTIVE ? EFFECTIVELY_FINAL_FIELDS_DV : MUTABLE_DV;
            case 1 -> EFFECTIVELY_IMMUTABLE_HC_DV;
            case 2 -> EFFECTIVELY_IMMUTABLE_DV;
            default -> throw new UnsupportedOperationException();
        };
    }

    public static DV eventuallyImmutable(int level) {
        return switch (level) {
            case 0 -> EVENTUALLY_FINAL_FIELDS_DV;
            case 1 -> EVENTUALLY_IMMUTABLE_HC_DV;
            case 2 -> EVENTUALLY_IMMUTABLE_DV;
            default -> throw new UnsupportedOperationException();
        };
    }

    public static DV beforeImmutableDv(int level) {
        return switch (level) {
            case 0 -> EVENTUALLY_FINAL_FIELDS_BEFORE_MARK_DV;
            case 1 -> EVENTUALLY_IMMUTABLE_HC_BEFORE_MARK_DV;
            case 2 -> EVENTUALLY_IMMUTABLE_BEFORE_MARK_DV;
            default -> throw new UnsupportedOperationException();
        };
    }

    public static DV afterImmutableDv(int level) {
        return switch (level) {
            case 0 -> EVENTUALLY_FINAL_FIELDS_AFTER_MARK_DV;
            case 1 -> EVENTUALLY_IMMUTABLE_HC_AFTER_MARK_DV;
            case 2 -> EVENTUALLY_IMMUTABLE_AFTER_MARK_DV;
            default -> throw new UnsupportedOperationException();
        };
    }

    public static boolean isAfterThrowWhenNotEventual(DV dv) {
        if (dv.isDelayed()) return false;
        assert dv.value() >= 0;
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
        assert dv.value() >= 0;
        Effective effective = effective(dv);
        return effective == EVENTUAL_BEFORE || effective == EVENTUAL;
    }

    public static DV composeOneLevelLessNotNull(DV dv) {
        if (dv.isDelayed()) return dv;
        int level = level(dv);
        if (level == NOT_NULL_1.level) return EFFECTIVELY_NOT_NULL_DV;
        return NULLABLE_DV;
    }

    public static DV composeOneLevelMoreNotNull(DV dv) {
        if (dv.isDelayed()) return dv;
        assert dv.value() >= 0;
        if (NULLABLE_DV.equals(dv)) return EFFECTIVELY_NOT_NULL_DV;
        return EFFECTIVELY_CONTENT_NOT_NULL_DV; // we're not going up a t m
    }

    public static DV independentCorrespondingToImmutable(DV immutable) {
        if (immutable.isDelayed()) return immutable;
        return independentCorrespondingToImmutableLevelDv(level(immutable));
    }

    public static DV independentCorrespondingToImmutableLevelDv(int immutableLevel) {
        return switch (immutableLevel) {
            case 0 -> DEPENDENT_DV;
            case 1 -> INDEPENDENT_HC_DV;
            case 2 -> INDEPENDENT_DV;
            default -> throw new UnsupportedOperationException();
        };
    }

    public static boolean independentConsistentWithImmutable(DV independent, DV immutable) {
        assert independent.isDone();
        assert immutable.isDone();
        int levelImmutable = MultiLevel.level(immutable);
        return switch (levelImmutable) {
            case 0 -> true;
            case 1 -> INDEPENDENT_HC_DV.le(independent);
            case 2 -> INDEPENDENT_DV.equals(independent);
            default -> throw new UnsupportedOperationException();
        };
    }

    /*
    "yes" to eventually, "yes" to eventually_after but "no" to eventually_before
     */
    public static boolean isAtLeastEventuallyRecursivelyImmutable(DV immutable) {
        return immutable.ge(EVENTUALLY_IMMUTABLE_DV);
    }

    public static Effective effectiveAtFinalFields(DV dv) {
        int level = MultiLevel.level(dv);
        if (level < MUTABLE.level) return FALSE;
        return MultiLevel.effective(dv);
    }

    public static Effective effectiveAtImmutableLevel(DV dv) {
        int level = MultiLevel.level(dv);
        if (level < IMMUTABLE_HC.level) return FALSE;
        return MultiLevel.effective(dv);
    }

    public static boolean isEffectivelyImmutable(DV immutable) {
        return level(immutable) >= IMMUTABLE_HC.level && effective(immutable) == EFFECTIVE;
    }

    public static boolean isAtLeastIndependentHC(DV independent) {
        return independent.ge(MultiLevel.INDEPENDENT_HC_DV);
    }

    public static int correspondingImmutableLevel(DV correctedIndependent) {
        if (correctedIndependent.equals(MultiLevel.DEPENDENT_DV)) {
            throw new UnsupportedOperationException("Already in negative");
        }
        if (MultiLevel.INDEPENDENT_HC_DV.equals(correctedIndependent)) {
            return MultiLevel.Level.IMMUTABLE_HC.level;
        }
        if (MultiLevel.INDEPENDENT_DV.equals(correctedIndependent)) {
            return MultiLevel.Level.IMMUTABLE.level;
        }
        throw new UnsupportedOperationException();
    }


}

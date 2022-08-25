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

package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.FinalFields;
import org.e2immu.annotation.Immutable;
import org.e2immu.annotation.ImmutableContainer;
import org.e2immu.annotation.eventual.BeforeMark;
import org.e2immu.annotation.type.UtilityClass;

import java.util.HashMap;
import java.util.Map;

import static org.e2immu.analyser.parser.E2ImmuAnnotationExpressions.*;

/*
in a separate class to make this unit testable; there's quite a few cases...
 */

@UtilityClass
public class GenerateAnnotationsImmutableAndContainer {
    public static final Map<String, Object> NO_PARAMS = Map.of();
    public static final Map<String, Object> IMPLIED_MAP = Map.of(IMPLIED, true);

    private GenerateAnnotationsImmutableAndContainer() {
        throw new UnsupportedOperationException();
    }

    // for testing
    public static Map<Class<?>, Map<String, Object>> generate(DV immutable, DV container, boolean isType) {
        return generate(immutable, container, isType, "abc", true,
                true, null, false);

    }

    // for testing
    public static Map<Class<?>, Map<String, Object>> generate(DV immutable,
                                                              DV container,
                                                              boolean isType,
                                                              String mark,
                                                              boolean immutableBetterThanFormal,
                                                              boolean containerBetterThanFormal) {
        return generate(immutable, container, isType, mark, immutableBetterThanFormal, containerBetterThanFormal,
                null, false);

    }

    public static Map<Class<?>, Map<String, Object>> generate(DV immutable,
                                                              DV container,
                                                              boolean isType,
                                                              String mark,
                                                              boolean immutableBetterThanFormal,
                                                              boolean containerBetterThanFormal,
                                                              String constantValue,
                                                              boolean constantImplied) {
        if (immutable.isDelayed()) {
            return Map.of();
        }
        boolean haveContainer = container.equals(MultiLevel.CONTAINER_DV);
        boolean containerInconclusive = container.isDelayed();

        MultiLevel.Effective effective = MultiLevel.effective(immutable);
        int level = MultiLevel.level(immutable);

        // EVENTUAL
        if (effective == MultiLevel.Effective.EVENTUAL) {
            if (isType || immutableBetterThanFormal && !mark.isBlank()) {
                return map(level, haveContainer, containerInconclusive, containerBetterThanFormal, Map.of(AFTER, mark));
            }
            if (immutableBetterThanFormal || haveContainer && containerBetterThanFormal) {
                return map(level, haveContainer, containerInconclusive, containerBetterThanFormal, Map.of());
            }
            return map(level, haveContainer, containerInconclusive, containerBetterThanFormal, Map.of(IMPLIED, true));
        }

        // BEFORE
        if (effective == MultiLevel.Effective.EVENTUAL_BEFORE) {
            if (isType) throw new UnsupportedOperationException(); // cannot have this on a type
            return Map.of(BeforeMark.class, NO_PARAMS);
        }

        // AFTER
        if (effective == MultiLevel.Effective.EVENTUAL_AFTER) {
            if (isType) throw new UnsupportedOperationException(); // cannot have this on a type
            return map(level, haveContainer, containerInconclusive, containerBetterThanFormal, NO_PARAMS);
        }

        // EFFECTIVE
        if (effective == MultiLevel.Effective.EFFECTIVE) {
            if (constantValue != null && level == MultiLevel.Level.IMMUTABLE_R.level) {
                Map<String, Object> map = constantImplied ? Map.of(VALUE, constantValue, IMPLIED, true)
                        : Map.of(VALUE, constantValue);
                return map(level, true, false, false, map);
            }
            if (isType || immutableBetterThanFormal || haveContainer && containerBetterThanFormal) {
                return map(level, haveContainer, containerInconclusive, containerBetterThanFormal, NO_PARAMS);
            }
            return map(level, haveContainer, containerInconclusive, containerBetterThanFormal, Map.of(IMPLIED, true));
        }

        Map<Class<?>, Map<String, Object>> res = new HashMap<>();
        if (containerInconclusive) {
            res.put(Container.class, Map.of(INCONCLUSIVE, true));
        } else if (haveContainer) {
            if (isType || immutableBetterThanFormal) {
                res.put(Container.class, NO_PARAMS);
            } else {
                res.put(Container.class, Map.of(IMPLIED, true));
            }
        }
        if (immutable.isInconclusive()) {
            res.put(Immutable.class, Map.of(INCONCLUSIVE, true));
        }
        return res;
    }

    private static Map<Class<?>, Map<String, Object>> map(int level,
                                                          boolean container,
                                                          boolean containerInconclusive,
                                                          boolean containerBetterThanFormal,
                                                          Map<String, Object> immutableParams) {
        Map<String, Object> params = new HashMap<>(immutableParams);

        if (level == MultiLevel.Level.IMMUTABLE_1.level) {
            if (container) {
                if (containerBetterThanFormal) {
                    return Map.of(FinalFields.class, params, Container.class, NO_PARAMS);
                }
                return Map.of(FinalFields.class, params, Container.class, IMPLIED_MAP);
            }
            if (containerInconclusive) {
                return Map.of(FinalFields.class, params, Container.class, Map.of(INCONCLUSIVE, true));
            }
            return Map.of(FinalFields.class, params);
        }
        // apart from the parameter HIDDEN_CONTENT, there is no difference between the two immutable states
        if (level == MultiLevel.Level.IMMUTABLE_2.level) {
            params.put(HIDDEN_CONTENT, true);
        }
        if (level >= MultiLevel.Level.IMMUTABLE_2.level) {
            if (container) {
                // here, we do not care about containerImplied
                return Map.of(ImmutableContainer.class, params,
                        Immutable.class, IMPLIED_MAP,
                        Container.class, IMPLIED_MAP,
                        FinalFields.class, IMPLIED_MAP);
            }
            if (containerInconclusive) {
                return Map.of(Immutable.class, params, Container.class, Map.of(INCONCLUSIVE, true),
                        FinalFields.class, IMPLIED_MAP);
            }
            return Map.of(Immutable.class, params, FinalFields.class, IMPLIED_MAP);
        }
        throw new UnsupportedOperationException();
    }
}

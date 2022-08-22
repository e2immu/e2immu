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

/*
in a separate class to make this unit testable; there's quite a few cases...
 */

@UtilityClass
public class GenerateAnnotationsImmutable {
    public static final Map<String, Object> TRUE = Map.of();

    private GenerateAnnotationsImmutable() {
        throw new UnsupportedOperationException();
    }

    // for testing
    public static Map<Class<?>, Map<String, Object>> generate(DV immutable, DV container, boolean isType) {
        return generate(immutable, container, isType, "abc", false, false);
    }

    public static Map<Class<?>, Map<String, Object>> generate(DV immutable,
                                                              DV container,
                                                              boolean isType,
                                                              String mark,
                                                              boolean betterThanFormal,
                                                              boolean inconclusive) {
        if (immutable.isDelayed()) return Map.of();
        boolean haveContainer = container.equals(MultiLevel.CONTAINER_DV);
        MultiLevel.Effective effective = MultiLevel.effective(immutable);
        int level = MultiLevel.level(immutable);

        // EVENTUAL
        if (effective == MultiLevel.Effective.EVENTUAL) {
            if (isType || betterThanFormal && !mark.isBlank()) {
                return map(level, haveContainer, Map.of("after", mark), inconclusive);
            }
            if (betterThanFormal) {
                return map(level, haveContainer, Map.of(), inconclusive);
            }
            return map(level, haveContainer, Map.of("implied", true), inconclusive);
        }

        // BEFORE
        if (effective == MultiLevel.Effective.EVENTUAL_BEFORE) {
            if (isType) throw new UnsupportedOperationException(); // cannot have this on a type
            return Map.of(BeforeMark.class, TRUE);
        }

        // AFTER
        if (effective == MultiLevel.Effective.EVENTUAL_AFTER) {
            if (isType) throw new UnsupportedOperationException(); // cannot have this on a type
            return map(level, haveContainer, TRUE, inconclusive);
        }

        // EFFECTIVE
        if (effective == MultiLevel.Effective.EFFECTIVE) {
            if (isType || betterThanFormal) {
                return map(level, haveContainer, TRUE, inconclusive);
            }
            return map(level, haveContainer, Map.of("implied", true), inconclusive);
        }

        Map<Class<?>, Map<String, Object>> res = new HashMap<>();
        if (haveContainer) {
            if (isType || betterThanFormal) {
                res.put(Container.class, TRUE);
            } else {
                res.put(Container.class, Map.of("implied", true));
            }
        }
        return res;
    }

    private static Map<Class<?>, Map<String, Object>> map(int level,
                                                          boolean container,
                                                          Map<String, Object> add,
                                                          boolean inconclusive) {
        Map<String, Object> params = new HashMap<>(add);
        if (inconclusive) {
            params.put("inconclusive", true);
        }
        if (level == MultiLevel.Level.IMMUTABLE_1.level) {
            if (container) return Map.of(FinalFields.class, params, Container.class, TRUE);
            return Map.of(FinalFields.class, params);
        }
        if (level == MultiLevel.Level.IMMUTABLE_2.level) {
            params.put("hc", true);
            if (container) return Map.of(ImmutableContainer.class, params);
            return Map.of(Immutable.class, params);
        }
        if (level == MultiLevel.Level.IMMUTABLE_R.level) {
            if (container) return Map.of(ImmutableContainer.class, params);
            return Map.of(Immutable.class, params);
        }
        throw new UnsupportedOperationException();
    }
}

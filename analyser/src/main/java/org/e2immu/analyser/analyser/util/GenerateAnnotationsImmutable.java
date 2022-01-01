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
import org.e2immu.annotation.*;

import java.util.HashMap;
import java.util.Map;

/*
in a separate class to make this unit testable; there's too many cases...
 */

@UtilityClass
public class GenerateAnnotationsImmutable {
    public static final Map<String, Object> TRUE = Map.of();

    private GenerateAnnotationsImmutable() {
        throw new UnsupportedOperationException();
    }

    // for testing
    public static Map<Class<?>, Map<String, Object>> generate(DV immutable, DV container, boolean isType) {
        return generate(immutable, container, isType, false, "abc", false);
    }

    public static Map<Class<?>, Map<String, Object>> generate(DV immutable, DV container,
                                                              boolean isType,
                                                              boolean isInterface,
                                                              String mark, boolean betterThanFormal) {
        boolean haveContainer = container.valueIsTrue();
        MultiLevel.Effective effective = MultiLevel.effective(immutable);
        int level = MultiLevel.level(immutable);

        // EVENTUAL
        if (effective == MultiLevel.Effective.EVENTUAL) {
            if (isType) {
                return map(level, haveContainer, Map.of("after", mark));
            }
            if (betterThanFormal) {
                return map(level, haveContainer, Map.of());
            }
            return Map.of();
        }

        // BEFORE
        if (effective == MultiLevel.Effective.EVENTUAL_BEFORE) {
            if (isType) throw new UnsupportedOperationException(); // cannot have this on a type
            return Map.of(BeforeMark.class, TRUE);
        }

        // AFTER
        if (effective == MultiLevel.Effective.EVENTUAL_AFTER) {
            if (isType) throw new UnsupportedOperationException(); // cannot have this on a type
            return map(level, haveContainer, TRUE);
        }

        // EFFECTIVE
        if (effective == MultiLevel.Effective.EFFECTIVE) {
            if (isType || betterThanFormal) {
                return map(level, haveContainer, TRUE);
            }
            return Map.of();
        }

        Map<Class<?>, Map<String, Object>> res = new HashMap<>();
        if (isType) {
            if (haveContainer) {
                res.put(Container.class, TRUE);
            } else if (!isInterface || container.valueIsFalse()) {
                res.put(MutableModifiesArguments.class, TRUE);
            }
        }
        return res;
    }

    private static Map<Class<?>, Map<String, Object>> map(int level, boolean container, Map<String, Object> add) {
        Map<String, Object> params = new HashMap<>(add);
        Class<?> clazz;
        if (level == MultiLevel.Level.IMMUTABLE_1.level) {
            clazz = container ? E1Container.class : E1Immutable.class;
        } else if (level == MultiLevel.Level.IMMUTABLE_2.level) {
            clazz = container ? E2Container.class : E2Immutable.class;
        } else if (level == MultiLevel.Level.IMMUTABLE_R.level) {
            if (container) {
                clazz = ERContainer.class;
            } else {
                params.put("recursive", true);
                clazz = E2Immutable.class;
            }
        } else {
            clazz = container ? E2Container.class : E2Immutable.class;
            params.put("level", Integer.toString(level + 1));
        }
        return Map.of(clazz, params);
    }
}

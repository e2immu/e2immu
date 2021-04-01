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

import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.annotation.*;

import java.util.HashMap;
import java.util.Map;

/*
in a separate class to make this unit testable; there's too many different cases...
 */

@UtilityClass
public class GenerateAnnotationsImmutable {
    public static final Map<String, String> TRUE = Map.of();

    private GenerateAnnotationsImmutable() {
        throw new UnsupportedOperationException();
    }

    // for testing
    public static Map<Class<?>, Map<String, String>> generate(int immutable, int container, boolean isType) {
        return generate(immutable, container, isType, false, "abc", false);
    }

    public static Map<Class<?>, Map<String, String>> generate(int immutable, int container,
                                                              boolean isType,
                                                              boolean isInterface,
                                                              String mark, boolean betterThanFormal) {
        Map<Class<?>, Map<String, String>> res = new HashMap<>();
        boolean haveContainer = container == Level.TRUE;

        int e1 = MultiLevel.value(immutable, MultiLevel.E1IMMUTABLE);
        int e2 = MultiLevel.value(immutable, MultiLevel.E2IMMUTABLE);

        // EVENTUAL
        if (e1 == MultiLevel.EVENTUAL || e2 == MultiLevel.EVENTUAL) {
            Class<?> key = e2 == MultiLevel.EVENTUAL ? e2(haveContainer) : e1(haveContainer);
            if (isType) {
                res.put(key, Map.of("after", mark));
            } else if (betterThanFormal) {
                res.put(key, TRUE);
            }
            return res;
        }

        // BEFORE
        if (e1 == MultiLevel.EVENTUAL_BEFORE || e2 == MultiLevel.EVENTUAL_BEFORE) {
            if (isType) throw new UnsupportedOperationException(); // cannot have this on a type
            res.put(BeforeMark.class, TRUE);
            return res;
        }

        // AFTER
        if (e2 == MultiLevel.EVENTUAL_AFTER) {
            if (isType) throw new UnsupportedOperationException(); // cannot have this on a type
            res.put(e2(haveContainer), TRUE);
            return res;
        }
        if (e1 == MultiLevel.EVENTUAL_AFTER) {
            if (isType) throw new UnsupportedOperationException(); // cannot have this on a type
            res.put(e1(haveContainer), TRUE);
            return res;
        }

        // EFFECTIVE
        if (e1 == MultiLevel.EFFECTIVE || e2 == MultiLevel.EFFECTIVE) {
            Class<?> key = e2 == MultiLevel.EFFECTIVE ? e2(haveContainer) : e1(haveContainer);
            if (isType || betterThanFormal) {
                res.put(key, TRUE);
            }
            return res;
        }

        if (isType) {
            if (haveContainer) {
                res.put(Container.class, TRUE);
            } else if (!isInterface || container == Level.FALSE) {
                res.put(MutableModifiesArguments.class, TRUE);
            }
        }
        return res;
    }

    private static Class<?> e1(boolean container) {
        return container ? E1Container.class : E1Immutable.class;
    }

    private static Class<?> e2(boolean container) {
        return container ? E2Container.class : E2Immutable.class;
    }
}

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
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.type.UtilityClass;

import java.util.HashMap;
import java.util.Map;

import static org.e2immu.analyser.parser.E2ImmuAnnotationExpressions.*;

/*
in a separate class to make this unit testable; there's quite a few cases...
 */

@UtilityClass
public class GenerateAnnotationsIndependent {
    public static final Map<String, Object> TRUE = Map.of();

    private GenerateAnnotationsIndependent() {
        throw new UnsupportedOperationException();
    }

    public static Map<Class<?>, Map<String, Object>> map(DV independent, boolean implied) {
        Map<String, Object> params = new HashMap<>();
        if (independent.isInconclusive()) {
            params.put(INCONCLUSIVE, true);
        } else if (implied) {
            params.put(IMPLIED, true);
        }
        if (MultiLevel.INDEPENDENT_1_DV.equals(independent)) {
            params.put(HIDDEN_CONTENT, true);
        } else if (MultiLevel.DEPENDENT_DV.equals(independent)) {
            params.put(ABSENT, true);
            params.put(IMPLIED, true);
        } else {
            assert MultiLevel.INDEPENDENT_DV.equals(independent);
        }
        return Map.of(Independent.class, params);
    }
}

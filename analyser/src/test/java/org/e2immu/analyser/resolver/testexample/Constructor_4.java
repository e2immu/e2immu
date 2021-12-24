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

package org.e2immu.analyser.resolver.testexample;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Constructor_4 {

    interface AnnotationExpression {

    }

    static class AnnotationExpressionImpl implements AnnotationExpression {
        AnnotationExpressionImpl(String s) {

        }
    }

    static class E2 {

        public String immutableAnnotation(Class<?> key, List<AnnotationExpression> list) {
            return key.getCanonicalName();
        }
    }

    public void method(E2 e2) {
        List<AnnotationExpression> list = new ArrayList<>();
        Map<Class<?>, String> map = Map.of(String.class, "String");
        for(Map.Entry<Class<?>, String> entry: map.entrySet()) {
            AnnotationExpression expression = new AnnotationExpressionImpl(e2.immutableAnnotation(entry.getKey(), list));
        }
    }
}

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

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MethodInspection;
import org.e2immu.analyser.model.ParameterizedType;

import java.util.Map;

public interface ErasureExpression extends Expression {

    enum MethodStatic {
        YES, NO, IGNORE;

        public static MethodStatic from(MethodInspection methodInspection) {
            return methodInspection.isStatic() ? YES : NO;
        }

        public boolean test(MethodInspection methodInspection) {
            return switch (this) {
                case IGNORE -> true;
                case YES -> methodInspection.isStatic();
                case NO -> !methodInspection.isStatic();
            };

        }
    }

    Map<ParameterizedType, MethodStatic> erasureTypes(TypeContext typeContext);

}

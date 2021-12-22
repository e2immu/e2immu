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
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.TypeInfo;

import java.util.Set;
import java.util.function.*;

public interface ErasureExpression extends Expression {

    Set<ParameterizedType> erasureTypes(TypeContext typeContext);

    /*
   we make no distinction between Function<X, Boolean> and Predicate<X>, and the various
   variants using primitives.
    */
    static ParameterizedType erasureType(int numberOfParameters, boolean isVoid, TypeContext typeContext) {
        Class<?> clazz = switch (numberOfParameters) {
            case 0 -> isVoid ? Runnable.class : Supplier.class;
            case 1 -> isVoid ? Consumer.class : Function.class;
            case 2 -> isVoid ? BiConsumer.class : BiFunction.class;
            default -> null;
        };
        TypeInfo typeInfo;
        if (clazz == null) {
            typeInfo = typeContext.typeMapBuilder.syntheticFunction(numberOfParameters, isVoid);
        } else {
            typeInfo = typeContext.getFullyQualified(clazz);
        }
        return typeInfo.asParameterizedType(typeContext);
    }
}

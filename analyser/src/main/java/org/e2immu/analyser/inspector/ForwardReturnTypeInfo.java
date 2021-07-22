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

package org.e2immu.analyser.inspector;

import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.parser.Primitives;

/*
Information about the return type of an expression, passed on in a forwarding way.
 */
public record ForwardReturnTypeInfo(ParameterizedType type, MethodTypeParameterMap sam) {

    // we'd rather have java.lang.Boolean, because as soon as type parameters are involved, primitives
    // are boxed
    public static ForwardReturnTypeInfo expectBoolean(TypeContext typeContext) {
        return new ForwardReturnTypeInfo(typeContext.getPrimitives()
                .boxedBooleanTypeInfo.asSimpleParameterizedType(), null);
    }

    public static final ForwardReturnTypeInfo NO_INFO = new ForwardReturnTypeInfo(null, null);

    public static ForwardReturnTypeInfo computeSAM(ParameterizedType type, TypeContext typeContext) {
        if (type == null || Primitives.isVoid(type)) return NO_INFO;
        MethodTypeParameterMap sam = type.findSingleAbstractMethodOfInterface(typeContext);
        if (sam != null) {
            sam = sam.expand(type.initialTypeParameterMap(typeContext));
        }
        return new ForwardReturnTypeInfo(type, sam);
    }

    public boolean isVoid() {
        return type != null && Primitives.isVoid(type) || sam != null && Primitives.isVoid(sam.getConcreteReturnType());
    }
}

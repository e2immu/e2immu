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

/**
 * Information about the return-type of an expression, passed on in a forwarding way.
 *
 * @param type    can be null for a constructor, or when erasure is true
 * @param erasure true when method or constructor argument parser is in erasure mode
 */
public record ForwardReturnTypeInfo(ParameterizedType type, boolean erasure) {

    public ForwardReturnTypeInfo(ParameterizedType type) {
        this(type, false);
    }

    // we'd rather have java.lang.Boolean, because as soon as type parameters are involved, primitives
    // are boxed
    public static ForwardReturnTypeInfo expectBoolean(TypeContext typeContext) {
        return new ForwardReturnTypeInfo(typeContext.getPrimitives()
                .boxedBooleanTypeInfo.asSimpleParameterizedType(), false);
    }

    public static ForwardReturnTypeInfo expectVoid(TypeContext typeContext) {
        return new ForwardReturnTypeInfo(typeContext.getPrimitives().voidParameterizedType, false);
    }

    public MethodTypeParameterMap computeSAM(TypeContext typeContext) {
        if (type == null || Primitives.isVoid(type)) return null;
        MethodTypeParameterMap sam = type.findSingleAbstractMethodOfInterface(typeContext);
        if (sam != null) {
            return sam.expand(type.initialTypeParameterMap(typeContext));
        }
        return null;
    }

    public boolean isVoid(TypeContext typeContext) {
        if (type == null) return false;
        if (Primitives.isVoid(type)) return true;
        MethodTypeParameterMap sam = computeSAM(typeContext);
        return sam.methodInspection != null && Primitives.isVoid(sam.getConcreteReturnType());
    }
}

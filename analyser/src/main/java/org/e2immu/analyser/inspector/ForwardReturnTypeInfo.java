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
import org.e2immu.analyser.parser.InspectionProvider;

/**
 * Information about the return-type of an expression, passed on in a forwarding way.
 * <p>
 * Simple cases: when created at an expression as a statement, 'void' is passed on as the type: we're not expecting any result.
 *
 * <p>
 * In the following example,
 * <code>map.entrySet().stream().map(e -> new KV(e.getKey(), e.getValue().toString()).collect(...)</code>
 * the Lambda in map(...) has the concrete type
 * <code>Function&lt;Map.Entry&lt;String,Object&gt;, ? extends R&gt</code>.
 * When computing the erased type of "e.getKey()", we will find "K", the formal type of Map.Entry.
 * The lambda expression is evaluated with R as the expected return type, so "type" will be "R",
 * and the "extra" map will contain "K maps to String", and "V maps to String".
 * <p>
 * We could have made "extra" a MethodTypeParameterMap but are trying to simply use a map first.
 * <p>
 * Essentially moving from map(...) to the evaluation of the expression in function of e, we go from the
 * functional interface to the 2nd type parameter in "type" and the concrete map of the 1st type parameter in extra.
 *
 * @param type    can be null for a constructor, or when erasure is true.
 *                Represents the expected return type of the expression to be evaluated.
 * @param erasure true when method or constructor argument parser is in erasure mode
 * @param extra   Sometimes we must pass on extra information that is not about the return type, but about the type
 *                parameters of the parameters of the SAM we're defining.
 */
public record ForwardReturnTypeInfo(ParameterizedType type, boolean erasure, TypeParameterMap extra) {

    public ForwardReturnTypeInfo() {
        this(null, false, TypeParameterMap.EMPTY);
    }

    public ForwardReturnTypeInfo(ParameterizedType type) {
        this(type, false, TypeParameterMap.EMPTY);
    }

    public ForwardReturnTypeInfo(ParameterizedType type, boolean erasure) {
        this(type, erasure, TypeParameterMap.EMPTY);
    }

    // we'd rather have java.lang.Boolean, because as soon as type parameters are involved, primitives
    // are boxed
    public static ForwardReturnTypeInfo expectBoolean(TypeContext typeContext) {
        return new ForwardReturnTypeInfo(typeContext.getPrimitives()
                .boxedBooleanTypeInfo().asSimpleParameterizedType(), false);
    }

    public static ForwardReturnTypeInfo expectVoid(TypeContext typeContext) {
        return new ForwardReturnTypeInfo(typeContext.getPrimitives().voidParameterizedType(), false);
    }

    public MethodTypeParameterMap computeSAM(InspectionProvider inspectionProvider) {
        if (type == null || type.isVoid()) return null;
        MethodTypeParameterMap sam = type.findSingleAbstractMethodOfInterface(inspectionProvider);
        if (sam != null) {
            return sam.expand(type.initialTypeParameterMap(inspectionProvider));
        }
        return null;
    }

    public boolean isVoid(TypeContext typeContext) {
        if (type == null) return false;
        if (type.isVoid()) return true;
        MethodTypeParameterMap sam = type.findSingleAbstractMethodOfInterface(typeContext);
        return sam != null && sam.methodInspection != null
                && sam.getConcreteReturnType(typeContext.getPrimitives()).isVoid();
    }

    public String toString(InspectionProvider inspectionProvider) {
        return "[FWD: " + (type == null ? "null" : type.detailedString(inspectionProvider)) + ", erasure: " + erasure + "]";
    }
}

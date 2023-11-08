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

package org.e2immu.analyser.parser;


import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.TypeInfo;

import java.util.List;
import java.util.Map;

public interface PrimitivesWithoutParameterizedType {

    // normally, this information is read from the annotated APIs
    void setInspectionOfBoxedTypesForTesting();

    void processEnum(TypeInfo typeInfo, List<FieldInfo> fields);

    TypeInfo primitiveByName(String asString);

    boolean isPreOrPostFixOperator(MethodInfo operator);

    boolean isPrefixOperator(MethodInfo operator);

    MethodInfo prePostFixToAssignment(MethodInfo operator);

    TypeInfo boxed(TypeInfo typeInfo);

    TypeInfo unboxed(TypeInfo typeInfo);

    MethodInfo assignOperatorInt();

    MethodInfo assignPlusOperatorInt();

    MethodInfo assignMinusOperatorInt();

    MethodInfo assignMultiplyOperatorInt();

    MethodInfo assignDivideOperatorInt();

    MethodInfo assignOrOperatorInt();

    MethodInfo assignAndOperatorInt();

    MethodInfo assignLeftShiftOperatorInt();

    MethodInfo assignSignedRightShiftOperatorInt();

    MethodInfo assignUnsignedRightShiftOperatorInt();

    MethodInfo assignXorOperatorInt();

    MethodInfo assignRemainderOperatorInt();

    MethodInfo plusOperatorInt();

    MethodInfo minusOperatorInt();

    MethodInfo multiplyOperatorInt();

    MethodInfo divideOperatorInt();

    MethodInfo orOperatorInt();

    MethodInfo andOperatorInt();

    MethodInfo xorOperatorInt();

    MethodInfo orOperatorBool();

    MethodInfo andOperatorBool();

    MethodInfo equalsOperatorObject();

    MethodInfo equalsOperatorInt();

    MethodInfo notEqualsOperatorObject();

    MethodInfo notEqualsOperatorInt();

    MethodInfo plusOperatorString();

    MethodInfo xorOperatorBool();

    MethodInfo bitwiseXorOperatorInt();

    MethodInfo leftShiftOperatorInt();

    MethodInfo signedRightShiftOperatorInt();

    MethodInfo unsignedRightShiftOperatorInt();

    MethodInfo greaterOperatorInt();

    MethodInfo greaterEqualsOperatorInt();

    MethodInfo lessEqualsOperatorInt();

    MethodInfo lessOperatorInt();

    MethodInfo remainderOperatorInt();

    TypeInfo stringTypeInfo();

    TypeInfo booleanTypeInfo();

    TypeInfo charTypeInfo();

    TypeInfo classTypeInfo();

    MethodInfo logicalNotOperatorBool();

    MethodInfo unaryMinusOperatorInt();

    MethodInfo unaryPlusOperatorInt();

    MethodInfo prefixIncrementOperatorInt();

    MethodInfo postfixIncrementOperatorInt();

    MethodInfo prefixDecrementOperatorInt();

    MethodInfo postfixDecrementOperatorInt();

    MethodInfo bitWiseNotOperatorInt();

    TypeInfo integerTypeInfo();

    TypeInfo intTypeInfo();

    TypeInfo boxedBooleanTypeInfo();

    TypeInfo characterTypeInfo();

    TypeInfo objectTypeInfo();

    AnnotationExpression functionalInterfaceAnnotationExpression();

    Map<String, TypeInfo> getTypeByName();

    Map<String, TypeInfo> getPrimitiveByName();

    default boolean isEqualsOperator(MethodInfo operator) {
        return operator == equalsOperatorInt() || operator == equalsOperatorObject();
    }

    default boolean isNotEqualsOperator(MethodInfo operator) {
        return operator == notEqualsOperatorInt() || operator == notEqualsOperatorObject();
    }

    default TypeInfo ensureBoxed(TypeInfo typeInfo) {
        if (typeInfo != null && typeInfo.isPrimitiveExcludingVoid()) {
            return boxed(typeInfo);
        }
        return typeInfo;
    }
}

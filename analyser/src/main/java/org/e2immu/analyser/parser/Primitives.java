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

import org.e2immu.analyser.inspector.MethodInspectionImpl;
import org.e2immu.analyser.inspector.ParameterInspectionImpl;
import org.e2immu.analyser.model.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface Primitives {
    String JAVA_LANG = "java.lang";
    String JAVA_PRIMITIVE = "__java.lang__PRIMITIVE"; // special string, caught by constructor
    String ORG_E2IMMU_ANNOTATION = "org.e2immu.annotation";
    String JAVA_LANG_OBJECT = "java.lang.Object";

    String UNARY_MINUS_OPERATOR_INT = "int.-(int)";
    String LONG_FQN = "long";

    ParameterizedType stringParameterizedType();

    ParameterizedType intParameterizedType();

    ParameterizedType booleanParameterizedType();

    ParameterizedType longParameterizedType();

    ParameterizedType doubleParameterizedType();

    ParameterizedType floatParameterizedType();

    ParameterizedType shortParameterizedType();

    ParameterizedType charParameterizedType();

    static boolean isInt(TypeInfo typeInfo) {
        return "int".equals(typeInfo.fullyQualifiedName);
    }

    static boolean isInteger(TypeInfo typeInfo) {
        return "java.lang.Integer".equals(typeInfo.fullyQualifiedName);
    }

    static boolean isChar(TypeInfo typeInfo) {
        return "char".equals(typeInfo.fullyQualifiedName);
    }

    static boolean isCharacter(TypeInfo typeInfo) {
        return "java.lang.Character".equals(typeInfo.fullyQualifiedName);
    }

    static boolean isBoolean(TypeInfo typeInfo) {
        return typeInfo != null && "boolean".equals(typeInfo.fullyQualifiedName);
    }

    static boolean isBoolean(ParameterizedType pt) {
        if (pt == null || pt.arrays != 0) return false;
        return pt.typeInfo != null && "boolean".equals(pt.typeInfo.fullyQualifiedName);
    }

    static boolean isBoxedBoolean(TypeInfo typeInfo) {
        return typeInfo != null && "java.lang.Boolean".equals(typeInfo.fullyQualifiedName);
    }

    static boolean isNotBooleanOrBoxedBoolean(ParameterizedType parameterizedType) {
        if (parameterizedType.typeInfo == null) return true; // for parameterized types
        return !isBoolean(parameterizedType.typeInfo) && !isBoxedBoolean(parameterizedType.typeInfo);
    }

    static boolean isLong(TypeInfo typeInfo) {
        return "long".equals(typeInfo.fullyQualifiedName);
    }

    static boolean isBoxedLong(TypeInfo typeInfo) {
        return "java.lang.Long".equals(typeInfo.fullyQualifiedName);
    }

    static boolean isShort(TypeInfo typeInfo) {
        return "short".equals(typeInfo.fullyQualifiedName);
    }

    static boolean isBoxedShort(TypeInfo typeInfo) {
        return "java.lang.Short".equals(typeInfo.fullyQualifiedName);
    }

    static boolean isByte(TypeInfo typeInfo) {
        return typeInfo != null && "byte".equals(typeInfo.fullyQualifiedName);
    }

    static boolean isBoxedByte(TypeInfo typeInfo) {
        return typeInfo != null && "java.lang.Byte".equals(typeInfo.fullyQualifiedName);
    }

    static boolean isDouble(TypeInfo typeInfo) {
        return "double".equals(typeInfo.fullyQualifiedName);
    }

    static boolean isBoxedDouble(TypeInfo typeInfo) {
        return "java.lang.Double".equals(typeInfo.fullyQualifiedName);
    }

    static boolean isFloat(TypeInfo typeInfo) {
        return "float".equals(typeInfo.fullyQualifiedName);
    }

    static boolean isBoxedFloat(TypeInfo typeInfo) {
        return "java.lang.Float".equals(typeInfo.fullyQualifiedName);
    }

    static boolean isVoid(TypeInfo typeInfo) {
        return "void".equals(typeInfo.fullyQualifiedName);
    }

    static boolean isJavaLangVoid(TypeInfo typeInfo) {
        return "java.lang.Void".equals(typeInfo.fullyQualifiedName);
    }

    static boolean isVoid(ParameterizedType parameterizedType) {
        return parameterizedType.typeInfo != null && isVoid(parameterizedType.typeInfo);
    }

    static boolean isVoidOrJavaLangVoid(ParameterizedType parameterizedType) {
        return parameterizedType.typeInfo != null && (isJavaLangVoid(parameterizedType.typeInfo) || isVoid(parameterizedType.typeInfo));
    }

    static boolean isJavaLangString(TypeInfo typeInfo) {
        return "java.lang.String".equals(typeInfo.fullyQualifiedName);
    }

    static boolean isJavaLangString(ParameterizedType parameterizedType) {
        return parameterizedType.typeInfo != null && isJavaLangString(parameterizedType.typeInfo);
    }

    static boolean isFunctionalInterfaceAnnotation(TypeInfo typeInfo) {
        return "java.lang.FunctionalInterface".equals(typeInfo.fullyQualifiedName);
    }

    static boolean isJavaLangObject(TypeInfo typeInfo) {
        return JAVA_LANG_OBJECT.equals(typeInfo.fullyQualifiedName);
    }

    static boolean isJavaLangObject(ParameterizedType parameterizedType) {
        return parameterizedType.arrays == 0 &&
                parameterizedType.typeInfo != null && isJavaLangObject(parameterizedType.typeInfo);
    }

    static boolean needsParent(TypeInfo typeInfo) {
        return typeInfo.fullyQualifiedName.indexOf('.') > 0 &&
                !typeInfo.fullyQualifiedName.startsWith("java.lang") &&
                !typeInfo.fullyQualifiedName.startsWith("jdk.internal");
    }

    static boolean isNotJavaLang(TypeInfo typeInfo) {
        return typeInfo == null || !typeInfo.fullyQualifiedName.startsWith("java.lang.");
    }

    static boolean allowInImport(TypeInfo ti) {
        return Primitives.isNotJavaLang(ti) && !isPrimitiveExcludingVoid(ti) && !isVoid(ti);
    }

    static boolean isPostfix(MethodInfo operator) {
        return (operator.name.equals("++") || operator.name.equals("--")) && operator.returnType().typeInfo != null &&
                operator.returnType().typeInfo.fullyQualifiedName.equals(LONG_FQN);
    }

    static boolean isUnaryNot(MethodInfo operator) {
        return operator.name.equals("!");
    }

    static boolean isBinaryAnd(MethodInfo operator) {
        return operator.name.equals("&&");
    }

    static boolean isUnaryMinusOperatorInt(MethodInfo operator) {
        return UNARY_MINUS_OPERATOR_INT.equals(operator.fullyQualifiedName()) && operator.methodInspection.get().getParameters().size() == 1;
    }

    static boolean isPrimitiveExcludingVoid(ParameterizedType parameterizedType) {
        return parameterizedType.arrays == 0 && isPrimitiveExcludingVoid(parameterizedType.typeInfo);
    }

    static boolean isPrimitiveExcludingVoid(TypeInfo typeInfo) {
        if (typeInfo == null) return false;
        return isByte(typeInfo) || isShort(typeInfo) || isInt(typeInfo) || isLong(typeInfo) ||
                isChar(typeInfo) || isFloat(typeInfo) || isDouble(typeInfo) || isBoolean(typeInfo);
    }

    static boolean isBoxedExcludingVoid(ParameterizedType parameterizedType) {
        return parameterizedType.arrays == 0 &&
                isBoxedExcludingVoid(parameterizedType.typeInfo);
    }

    static boolean isBoxedExcludingVoid(TypeInfo typeInfo) {
        if (typeInfo == null) return false;
        return isBoxedByte(typeInfo) || isBoxedShort(typeInfo) || isInteger(typeInfo) || isBoxedLong(typeInfo)
                || isCharacter(typeInfo) || isBoxedFloat(typeInfo) || isBoxedDouble(typeInfo) || isBoxedBoolean(typeInfo);
    }

    static boolean isDiscrete(ParameterizedType parameterizedType) {
        TypeInfo typeInfo = parameterizedType.typeInfo;
        if (parameterizedType.arrays != 0 || parameterizedType.typeInfo == null) return false;
        return isInt(typeInfo) || isInteger(typeInfo) ||
                isLong(typeInfo) || isBoxedLong(typeInfo) ||
                isShort(typeInfo) || isBoxedShort(typeInfo) ||
                isByte(typeInfo) || isBoxedByte(typeInfo);
    }

    static boolean isNumeric(ParameterizedType parameterizedType) {
        return parameterizedType != null && parameterizedType.arrays == 0 && isNumeric(parameterizedType.typeInfo);
    }

    static boolean isNumeric(TypeInfo typeInfo) {
        if (typeInfo == null) return false;
        return isInt(typeInfo) || isInteger(typeInfo) ||
                isLong(typeInfo) || isBoxedLong(typeInfo) ||
                isShort(typeInfo) || isBoxedShort(typeInfo) ||
                isByte(typeInfo) || isBoxedByte(typeInfo) ||
                isFloat(typeInfo) || isBoxedFloat(typeInfo) ||
                isDouble(typeInfo) || isBoxedDouble(typeInfo);
    }

    default MethodInfo createOperator(TypeInfo owner, String name, List<ParameterizedType> parameterizedTypes, ParameterizedType returnType) {
        int i = 0;
        MethodInspectionImpl.Builder builder = new MethodInspectionImpl.Builder(owner, name).setStatic(true);
        for (ParameterizedType parameterizedType : parameterizedTypes) {
            ParameterInspectionImpl.Builder pb = new ParameterInspectionImpl.Builder(Identifier.generate(),
                    parameterizedType, "p" + i, i++);
            builder.addParameter(pb); // inspection built when method is built
        }
        builder.setReturnType(returnType);
        return builder.build(InspectionProvider.DEFAULT).getMethodInfo();
    }

    // normally, this information is read from the annotated APIs
    void setInspectionOfBoxedTypesForTesting();

    void processEnum(TypeInfo typeInfo, List<FieldInfo> fields);

    ParameterizedType widestType(ParameterizedType t1, ParameterizedType t2);

    int primitiveTypeOrder(ParameterizedType pt);

    TypeInfo primitiveByName(String asString);

    int isAssignableFromTo(ParameterizedType from, ParameterizedType to, boolean covariant);

    boolean isPreOrPostFixOperator(MethodInfo operator);

    boolean isPrefixOperator(MethodInfo operator);

    MethodInfo prePostFixToAssignment(MethodInfo operator);

    TypeInfo boxed(TypeInfo typeInfo);

    Set<ParameterizedType> explicitTypesOfJLO();

    MethodInfo assignOperatorInt();

    MethodInfo assignPlusOperatorInt();

    MethodInfo assignMinusOperatorInt();

    MethodInfo assignMultiplyOperatorInt();

    MethodInfo assignDivideOperatorInt();

    MethodInfo assignOrOperatorInt();

    MethodInfo assignAndOperatorInt();

    MethodInfo plusOperatorInt();

    MethodInfo minusOperatorInt();

    MethodInfo multiplyOperatorInt();

    MethodInfo divideOperatorInt();

    MethodInfo bitwiseOrOperatorInt();

    MethodInfo bitwiseAndOperatorInt();

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

    ParameterizedType byteParameterizedType();

    TypeInfo classTypeInfo();

    ParameterizedType objectParameterizedType();

    ParameterizedType voidParameterizedType();

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

    TypeInfo objectTypeInfo();

    AnnotationExpression functionalInterfaceAnnotationExpression();

    Map<String, TypeInfo> getTypeByName();

    Map<String, TypeInfo> getPrimitiveByName();
}

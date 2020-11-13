/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;

import java.util.*;

import static org.e2immu.analyser.model.ParameterizedType.NOT_ASSIGNABLE;

public class Primitives {
    public static final String JAVA_LANG = "java.lang";
    public static final String JAVA_LANG_OBJECT = "java.lang.Object";

    public static final String ORG_E2IMMU_ANNOTATION = "org.e2immu.annotation";

    public final TypeInfo intTypeInfo = new TypeInfo("int");
    public final ParameterizedType intParameterizedType = intTypeInfo.asParameterizedType();

    public static boolean isInt(TypeInfo typeInfo) {
        return "int".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo integerTypeInfo = new TypeInfo("java.lang.Integer");

    public static boolean isInteger(TypeInfo typeInfo) {
        return "java.lang.Integer".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo charTypeInfo = new TypeInfo("char");
    public final ParameterizedType charParameterizedType = charTypeInfo.asParameterizedType();

    public static boolean isChar(TypeInfo typeInfo) {
        return "char".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo characterTypeInfo = new TypeInfo("java.lang.Character");

    public static boolean isCharacter(TypeInfo typeInfo) {
        return "java.lang.Character".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo booleanTypeInfo = new TypeInfo("boolean");
    public final ParameterizedType booleanParameterizedType = booleanTypeInfo.asParameterizedType();

    public static boolean isBoolean(TypeInfo typeInfo) {
        return "boolean".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo boxedBooleanTypeInfo = new TypeInfo("java.lang.Boolean");

    public static boolean isBoxedBoolean(TypeInfo typeInfo) {
        return "java.lang.Boolean".equals(typeInfo.fullyQualifiedName);
    }

    public static boolean isBooleanOrBoxedBoolean(ParameterizedType parameterizedType) {
        if (parameterizedType.typeInfo == null) return false; // for parameterized types
        return isBoolean(parameterizedType.typeInfo) || isBoxedBoolean(parameterizedType.typeInfo);
    }

    public final TypeInfo longTypeInfo = new TypeInfo("long");
    public final ParameterizedType longParameterizedType = longTypeInfo.asParameterizedType();

    public static boolean isLong(TypeInfo typeInfo) {
        return "long".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo boxedLongTypeInfo = new TypeInfo("java.lang.Long");

    public static boolean isBoxedLong(TypeInfo typeInfo) {
        return "java.lang.Long".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo shortTypeInfo = new TypeInfo("short");
    public final ParameterizedType shortParameterizedType = shortTypeInfo.asParameterizedType();

    public static boolean isShort(TypeInfo typeInfo) {
        return "short".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo boxedShortTypeInfo = new TypeInfo("java.lang.Short");

    public static boolean isBoxedShort(TypeInfo typeInfo) {
        return "java.lang.Short".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo byteTypeInfo = new TypeInfo("byte");
    public final ParameterizedType byteParameterizedType = byteTypeInfo.asParameterizedType();

    public static boolean isByte(TypeInfo typeInfo) {
        return "byte".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo boxedByteTypeInfo = new TypeInfo("java.lang.Byte");

    public static boolean isBoxedByte(TypeInfo typeInfo) {
        return "java.lang.Byte".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo doubleTypeInfo = new TypeInfo("double");
    public final ParameterizedType doubleParameterizedType = doubleTypeInfo.asParameterizedType();

    public static boolean isDouble(TypeInfo typeInfo) {
        return "double".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo boxedDoubleTypeInfo = new TypeInfo("java.lang.Double");

    public static boolean isBoxedDouble(TypeInfo typeInfo) {
        return "java.lang.Double".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo floatTypeInfo = new TypeInfo("float");
    public final ParameterizedType floatParameterizedType = floatTypeInfo.asParameterizedType();

    public static boolean isFloat(TypeInfo typeInfo) {
        return "float".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo boxedFloatTypeInfo = new TypeInfo("java.lang.Float");

    public static boolean isBoxedFloat(TypeInfo typeInfo) {
        return "java.lang.Float".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo voidTypeInfo = new TypeInfo("void");
    public final ParameterizedType voidParameterizedType = voidTypeInfo.asParameterizedType();
    public final TypeInfo boxedVoidTypeInfo = new TypeInfo("java.lang.Void");

    public static boolean isVoid(TypeInfo typeInfo) {
        return "void".equals(typeInfo.fullyQualifiedName);
    }

    public static boolean isJavaLangVoid(TypeInfo typeInfo) {
        return "java.lang.Void".equals(typeInfo.fullyQualifiedName);
    }

    public static boolean isVoid(ParameterizedType parameterizedType) {
        return parameterizedType.typeInfo != null && (isJavaLangVoid(parameterizedType.typeInfo) || isVoid(parameterizedType.typeInfo));
    }

    public final TypeInfo stringTypeInfo = new TypeInfo(JAVA_LANG, "String");
    public final ParameterizedType stringParameterizedType = stringTypeInfo.asParameterizedType();

    public static boolean isJavaLangString(TypeInfo typeInfo) {
        return "java.lang.String".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo annotationTypeTypeInfo = new TypeInfo(ORG_E2IMMU_ANNOTATION, "AnnotationType");
    public final FieldInfo annotationTypeComputed = new FieldInfo(annotationTypeTypeInfo, "COMPUTED", annotationTypeTypeInfo);
    public final FieldInfo annotationTypeVerify = new FieldInfo(annotationTypeTypeInfo, "VERIFY", annotationTypeTypeInfo);
    public final FieldInfo annotationTypeVerifyAbsent = new FieldInfo(annotationTypeTypeInfo, "VERIFY_ABSENT", annotationTypeTypeInfo);
    public final FieldInfo annotationTypeContract = new FieldInfo(annotationTypeTypeInfo, "CONTRACT", annotationTypeTypeInfo);

    public final TypeInfo annotationModeTypeInfo = new TypeInfo(ORG_E2IMMU_ANNOTATION, "AnnotationMode");
    public final FieldInfo annotationModeDefensive = new FieldInfo(annotationTypeTypeInfo, "DEFENSIVE", annotationModeTypeInfo);
    public final FieldInfo annotationModeOffensive = new FieldInfo(annotationTypeTypeInfo, "OFFENSIVE", annotationModeTypeInfo);

    public final TypeInfo functionalInterface = new TypeInfo("java.lang.FunctionalInterface");
    public final AnnotationExpression functionalInterfaceAnnotationExpression =
            AnnotationExpression.fromAnalyserExpressions(functionalInterface, List.of());

    public static boolean isFunctionalInterfaceAnnotation(TypeInfo typeInfo) {
        return "java.lang.FunctionalInterface".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo classTypeInfo = new TypeInfo(JAVA_LANG, "Class");

    public final TypeInfo objectTypeInfo = new TypeInfo(JAVA_LANG, "Object");
    public final ParameterizedType objectParameterizedType = objectTypeInfo.asParameterizedType();

    public static boolean isJavaLangObject(TypeInfo typeInfo) {
        return "java.lang.Object".equals(typeInfo.fullyQualifiedName);
    }

    public static boolean isJavaLangObject(ParameterizedType parameterizedType) {
        return parameterizedType.typeInfo != null && isJavaLangObject(parameterizedType.typeInfo);
    }

    private MethodInfo createOperator(TypeInfo owner, String name, List<ParameterizedType> parameterizedTypes, ParameterizedType returnType) {
        MethodInfo methodInfo = new MethodInfo(owner, name, true);
        int i = 0;
        MethodInspection.MethodInspectionBuilder builder = new MethodInspection.MethodInspectionBuilder();
        for (ParameterizedType parameterizedType : parameterizedTypes) {
            ParameterInfo parameterInfo = new ParameterInfo(methodInfo, parameterizedType, "p" + i, i++);
            parameterInfo.parameterInspection.set(new ParameterInspection.ParameterInspectionBuilder().build());
            builder.addParameter(parameterInfo);
        }
        builder.setReturnType(returnType);
        methodInfo.methodInspection.set(builder.build(methodInfo));
        return methodInfo;
    }

    private final List<ParameterizedType> intInt = List.of(intParameterizedType, intParameterizedType);
    private final List<ParameterizedType> boolBool = List.of(booleanParameterizedType, booleanParameterizedType);

    public final MethodInfo plusOperatorInt = createOperator(intTypeInfo, "+", intInt, intParameterizedType);
    public final MethodInfo minusOperatorInt = createOperator(intTypeInfo, "-", intInt, intParameterizedType);
    public final MethodInfo bitwiseOrOperatorInt = createOperator(intTypeInfo, "|", intInt, intParameterizedType);
    public final MethodInfo bitwiseAndOperatorInt = createOperator(intTypeInfo, "&", intInt, intParameterizedType);
    public final MethodInfo bitwiseXorOperatorInt = createOperator(intTypeInfo, "^", intInt, intParameterizedType);
    public final MethodInfo remainderOperatorInt = createOperator(intTypeInfo, "%", intInt, intParameterizedType);
    public final MethodInfo signedRightShiftOperatorInt = createOperator(intTypeInfo, ">>", intInt, intParameterizedType);
    public final MethodInfo unsignedRightShiftOperatorInt = createOperator(intTypeInfo, ">>>", intInt, intParameterizedType);
    public final MethodInfo leftShiftOperatorInt = createOperator(intTypeInfo, "<<", intInt, intParameterizedType);
    public final MethodInfo divideOperatorInt = createOperator(intTypeInfo, "/", intInt, intParameterizedType);
    public final MethodInfo multiplyOperatorInt = createOperator(intTypeInfo, "*", intInt, intParameterizedType);

    public final MethodInfo equalsOperatorInt = createOperator(intTypeInfo, "==", intInt, booleanParameterizedType);
    public final MethodInfo notEqualsOperatorInt = createOperator(intTypeInfo, "!=", intInt, booleanParameterizedType);
    public final MethodInfo greaterOperatorInt = createOperator(intTypeInfo, ">", intInt, booleanParameterizedType);
    public final MethodInfo greaterEqualsOperatorInt = createOperator(intTypeInfo, ">=", intInt, booleanParameterizedType);
    public final MethodInfo lessOperatorInt = createOperator(intTypeInfo, "<", intInt, booleanParameterizedType);
    public final MethodInfo lessEqualsOperatorInt = createOperator(intTypeInfo, "<=", intInt, booleanParameterizedType);

    public final MethodInfo assignOperatorInt = createOperator(intTypeInfo, "=", List.of(intParameterizedType), intParameterizedType);
    public final MethodInfo assignPlusOperatorInt = createOperator(intTypeInfo, "+=", List.of(intParameterizedType), intParameterizedType);
    public final MethodInfo assignMinusOperatorInt = createOperator(intTypeInfo, "-=", List.of(intParameterizedType), intParameterizedType);
    public final MethodInfo assignMultiplyOperatorInt = createOperator(intTypeInfo, "*=", List.of(intParameterizedType), intParameterizedType);
    public final MethodInfo assignDivideOperatorInt = createOperator(intTypeInfo, "/=", List.of(intParameterizedType), intParameterizedType);
    public final MethodInfo assignOrOperatorBoolean = createOperator(intTypeInfo, "|=", List.of(intParameterizedType), intParameterizedType);

    public final MethodInfo postfixIncrementOperatorInt = createOperator(intTypeInfo, "++", List.of(), intParameterizedType);
    public final MethodInfo prefixIncrementOperatorInt = createOperator(intTypeInfo, "++", List.of(), intParameterizedType);
    public final MethodInfo postfixDecrementOperatorInt = createOperator(intTypeInfo, "--", List.of(), intParameterizedType);
    public final MethodInfo prefixDecrementOperatorInt = createOperator(intTypeInfo, "--", List.of(), intParameterizedType);

    public final MethodInfo unaryPlusOperatorInt = createOperator(intTypeInfo, "+", List.of(intParameterizedType), intParameterizedType);
    public final MethodInfo unaryMinusOperatorInt = createOperator(intTypeInfo, "-", List.of(intParameterizedType), intParameterizedType);

    public static boolean isUnaryMinusOperatorInt(MethodInfo operator) {
        return "int.-(int)".equals(operator.fullyQualifiedName()) && operator.methodInspection.get().parameters.size() == 1;
    }

    public final MethodInfo bitWiseNotOperatorInt = createOperator(intTypeInfo, "~", List.of(intParameterizedType), intParameterizedType);
    public final MethodInfo logicalNotOperatorBool = createOperator(booleanTypeInfo, "!", List.of(booleanParameterizedType), booleanParameterizedType);
    public final MethodInfo orOperatorBool = createOperator(booleanTypeInfo, "||", boolBool, booleanParameterizedType);
    public final MethodInfo andOperatorBool = createOperator(booleanTypeInfo, "&&", boolBool, booleanParameterizedType);

    public final MethodInfo plusOperatorString = createOperator(stringTypeInfo, "+", List.of(stringParameterizedType,
            stringParameterizedType), stringParameterizedType);

    public final MethodInfo equalsOperatorObject = createOperator(objectTypeInfo, "==",
            List.of(objectParameterizedType, objectParameterizedType), booleanParameterizedType);
    public final MethodInfo notEqualsOperatorObject = createOperator(objectTypeInfo, "!=",
            List.of(objectParameterizedType, objectParameterizedType), booleanParameterizedType);

    public final Map<String, TypeInfo> primitiveByName = new HashMap<>();
    public final Map<String, TypeInfo> typeByName = new HashMap<>();

    public final Set<TypeInfo> boxed = Set.of(boxedBooleanTypeInfo, boxedByteTypeInfo, boxedDoubleTypeInfo, boxedFloatTypeInfo,
            boxedLongTypeInfo, boxedShortTypeInfo, boxedVoidTypeInfo, integerTypeInfo, characterTypeInfo);

    public final Set<TypeInfo> primitives = Set.of(booleanTypeInfo, byteTypeInfo, doubleTypeInfo, floatTypeInfo,
            longTypeInfo, shortTypeInfo, voidTypeInfo, intTypeInfo, charTypeInfo);


    public Primitives() {
        for (TypeInfo ti : primitives) {
            ti.typeInspection.set(new TypeInspection.TypeInspectionBuilder()
                    .setPackageName(JAVA_LANG)
                    .setTypeNature(TypeNature.PRIMITIVE)
                    .setParentClass(objectParameterizedType)
                    .build(ti));
            primitiveByName.put(ti.simpleName, ti);
            TypeAnalysisImpl.Builder builder = new TypeAnalysisImpl.Builder(this, ti);
            ti.typeAnalysis.set(builder);
            builder.properties.put(VariableProperty.CONTAINER, Level.TRUE);
            builder.properties.put(VariableProperty.IMMUTABLE, MultiLevel.EFFECTIVELY_E2IMMUTABLE);
            builder.approvedPreconditions.freeze(); // cannot change these anymore; will never be eventual
            builder.properties.put(VariableProperty.MODIFIED, Level.FALSE);
            builder.implicitlyImmutableDataTypes.set(Set.of());
        }

        for (TypeInfo ti : List.of(stringTypeInfo, objectTypeInfo, classTypeInfo, annotationTypeTypeInfo, annotationModeTypeInfo, functionalInterface)) {
            typeByName.put(ti.simpleName, ti);
        }
        for (TypeInfo ti : boxed) {
            typeByName.put(ti.simpleName, ti);
        }

        processEnum(annotationTypeTypeInfo, List.of(annotationTypeComputed, annotationTypeContract, annotationTypeVerify, annotationTypeVerifyAbsent));
        processEnum(annotationModeTypeInfo, List.of(annotationModeDefensive, annotationModeOffensive));

        functionalInterface.typeInspection.set(new TypeInspection.TypeInspectionBuilder()
                .setPackageName("java.lang")
                .setTypeNature(TypeNature.ANNOTATION)
                .setParentClass(objectParameterizedType)
                .build(functionalInterface));
    }

    public static boolean isPrimitiveExcludingVoid(ParameterizedType parameterizedType) {
        return parameterizedType.typeInfo != null && isPrimitiveExcludingVoid(parameterizedType.typeInfo);
    }

    public static boolean isPrimitiveExcludingVoid(TypeInfo typeInfo) {
        return isByte(typeInfo) || isShort(typeInfo) || isInt(typeInfo) || isLong(typeInfo) ||
                isChar(typeInfo) || isFloat(typeInfo) || isDouble(typeInfo) || isBoolean(typeInfo);
    }

    public static boolean isBoxedExcludingVoid(TypeInfo typeInfo) {
        return isBoxedByte(typeInfo) || isBoxedShort(typeInfo) || isInteger(typeInfo) || isBoxedLong(typeInfo)
                || isCharacter(typeInfo) || isBoxedFloat(typeInfo) || isBoxedDouble(typeInfo) || isBoolean(typeInfo);
    }

    private void processEnum(TypeInfo typeInfo, List<FieldInfo> fields) {
        MethodInfo valueOf = new MethodInfo(typeInfo, "valueOf", true);
        ParameterInfo valueOf1 = new ParameterInfo(valueOf, stringParameterizedType, "s", 0);
        valueOf1.parameterInspection.set(new ParameterInspection.ParameterInspectionBuilder().build());
        valueOf.methodInspection.set(new MethodInspection.MethodInspectionBuilder()
                .setReturnType(typeInfo)
                .addParameter(valueOf1)
                .addModifier(MethodModifier.PUBLIC)
                .build(valueOf));
        MethodInfo name = new MethodInfo(typeInfo, "name", false);
        name.methodInspection.set(new MethodInspection.MethodInspectionBuilder()
                .setReturnType(stringTypeInfo)
                .addModifier(MethodModifier.PUBLIC)
                .build(name));
        TypeInspection.TypeInspectionBuilder typeInspectionBuilder = new TypeInspection.TypeInspectionBuilder()
                .setPackageName(ORG_E2IMMU_ANNOTATION)
                .setTypeNature(TypeNature.ENUM)
                .addTypeModifier(TypeModifier.PUBLIC)
                .setParentClass(objectParameterizedType)
                .addMethod(valueOf)
                .addMethod(name);
        for (FieldInfo fieldInfo : fields) typeInspectionBuilder.addField(fieldInfo);
        typeInfo.typeInspection.set(typeInspectionBuilder.build( typeInfo));
        for (FieldInfo fieldInfo : fields) {
            fieldInfo.fieldInspection.set(new FieldInspection.FieldInspectionBuilder()
                    .addModifiers(List.of(FieldModifier.STATIC, FieldModifier.FINAL, FieldModifier.PUBLIC))
                    .build());
        }
    }

    public ParameterizedType widestType(ParameterizedType t1, ParameterizedType t2) {
        int o1 = primitiveTypeOrder(Objects.requireNonNull(t1));
        int o2 = primitiveTypeOrder(Objects.requireNonNull(t2));
        if (o1 >= o2) return t1;
        return t2;
    }

    public int primitiveTypeOrder(ParameterizedType pt) {
        if (pt == null) throw new NullPointerException();
        if (pt.isType()) {
            TypeInfo typeInfo = pt.typeInfo;
            if (typeInfo == booleanTypeInfo) return 1;
            if (typeInfo == byteTypeInfo) return 2;
            if (typeInfo == charTypeInfo) return 3;
            if (typeInfo == shortTypeInfo) return 4;
            if (typeInfo == intTypeInfo) return 5;
            if (typeInfo == floatTypeInfo) return 6;
            if (typeInfo == longTypeInfo) return 7;
            if (typeInfo == doubleTypeInfo) return 8;
            if (typeInfo == stringTypeInfo) return 9;
        }
        return 0;
    }

    public TypeInfo primitiveByName(String asString) {
        TypeInfo ti = primitiveByName.get(asString);
        if (ti == null) throw new UnsupportedOperationException("Type " + asString + " not (yet) a primitive");
        return ti;
    }

    public int isAssignableFromTo(ParameterizedType from, ParameterizedType to) {
        int fromOrder = primitiveTypeOrder(from);
        if (fromOrder <= 1 || fromOrder >= 9) return NOT_ASSIGNABLE;
        int toOrder = primitiveTypeOrder(to);
        if (toOrder <= 1 || toOrder >= 9) return NOT_ASSIGNABLE;
        int diff = toOrder - fromOrder;
        return diff < 0 ? NOT_ASSIGNABLE : diff;
    }

    public boolean isPreOrPostFixOperator(MethodInfo operator) {
        return operator == postfixDecrementOperatorInt || // i--;
                operator == postfixIncrementOperatorInt || // i++;
                operator == prefixDecrementOperatorInt || // --i;
                operator == prefixIncrementOperatorInt; // ++i;
    }

    public boolean isPrefixOperator(MethodInfo operator) {
        return operator == prefixDecrementOperatorInt || operator == prefixIncrementOperatorInt;
    }

    public MethodInfo prePostFixToAssignment(MethodInfo operator) {
        if (operator == postfixDecrementOperatorInt || operator == prefixDecrementOperatorInt) {
            return assignMinusOperatorInt;
        }
        if (operator == postfixIncrementOperatorInt || operator == prefixIncrementOperatorInt) {
            return assignPlusOperatorInt;
        }
        throw new UnsupportedOperationException();
    }

    public TypeInfo boxed(TypeInfo typeInfo) {
        if (typeInfo == longTypeInfo)
            return boxedLongTypeInfo;
        if (typeInfo == intTypeInfo)
            return integerTypeInfo;
        if (typeInfo == shortTypeInfo)
            return boxedShortTypeInfo;
        if (typeInfo == byteTypeInfo)
            return boxedByteTypeInfo;
        if (typeInfo == charTypeInfo)
            return characterTypeInfo;
        if (typeInfo == booleanTypeInfo)
            return boxedBooleanTypeInfo;
        if (typeInfo == floatTypeInfo)
            return boxedFloatTypeInfo;
        if (typeInfo == doubleTypeInfo)
            return boxedDoubleTypeInfo;
        throw new UnsupportedOperationException();
    }

    public static boolean isDiscrete(ParameterizedType parameterizedType) {
        TypeInfo typeInfo = parameterizedType.typeInfo;
        if (parameterizedType.typeInfo == null) return false;
        return isInt(typeInfo) || isInteger(typeInfo) ||
                isLong(typeInfo) || isBoxedLong(typeInfo) ||
                isShort(typeInfo) || isBoxedShort(typeInfo) ||
                isByte(typeInfo) || isBoxedByte(typeInfo);
    }

    public static boolean isNumeric(TypeInfo typeInfo) {
        if (typeInfo == null) return false;
        return isInt(typeInfo) || isInteger(typeInfo) ||
                isLong(typeInfo) || isBoxedLong(typeInfo) ||
                isShort(typeInfo) || isBoxedShort(typeInfo) ||
                isByte(typeInfo) || isBoxedByte(typeInfo) ||
                isFloat(typeInfo) || isBoxedFloat(typeInfo) ||
                isDouble(typeInfo) || isBoxedDouble(typeInfo);
    }
}

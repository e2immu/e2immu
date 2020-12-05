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

import org.e2immu.analyser.analyser.TypeAnalysisImpl;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.inspector.FieldInspectionImpl;
import org.e2immu.analyser.inspector.MethodInspectionImpl;
import org.e2immu.analyser.inspector.ParameterInspectionImpl;
import org.e2immu.analyser.inspector.TypeInspectionImpl;
import org.e2immu.analyser.model.*;

import java.util.*;

import static org.e2immu.analyser.inspector.TypeInspectionImpl.InspectionState.BY_HAND;
import static org.e2immu.analyser.model.ParameterizedType.NOT_ASSIGNABLE;

public class Primitives {
    public static final String JAVA_LANG = "java.lang";
    private static final String JAVA_LANG_OBJECT = "java.lang.Object";
    public static final String JAVA_PRIMITIVE = "__java.lang__PRIMITIVE"; // special string, caught by constructor

    public static final String ORG_E2IMMU_ANNOTATION = "org.e2immu.annotation";
    private static final String UNARY_MINUS_OPERATOR_INT = "int.-(int)";
    private static final String LONG_FQN = "long";

    public final TypeInfo intTypeInfo = new TypeInfo(JAVA_PRIMITIVE, "int");
    public final ParameterizedType intParameterizedType = intTypeInfo.asSimpleParameterizedType();

    public static boolean isInt(TypeInfo typeInfo) {
        return "int".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo integerTypeInfo = new TypeInfo(JAVA_LANG, "Integer");

    public static boolean isInteger(TypeInfo typeInfo) {
        return "java.lang.Integer".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo charTypeInfo = new TypeInfo(JAVA_PRIMITIVE, "char");
    public final ParameterizedType charParameterizedType = charTypeInfo.asSimpleParameterizedType();

    public static boolean isChar(TypeInfo typeInfo) {
        return "char".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo characterTypeInfo = new TypeInfo(JAVA_LANG, "Character");

    public static boolean isCharacter(TypeInfo typeInfo) {
        return "java.lang.Character".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo booleanTypeInfo = new TypeInfo(JAVA_PRIMITIVE, "boolean");
    public final ParameterizedType booleanParameterizedType = booleanTypeInfo.asSimpleParameterizedType();

    public static boolean isBoolean(TypeInfo typeInfo) {
        return typeInfo != null && "boolean".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo boxedBooleanTypeInfo = new TypeInfo(JAVA_LANG, "Boolean");

    public static boolean isBoxedBoolean(TypeInfo typeInfo) {
        return typeInfo != null && "java.lang.Boolean".equals(typeInfo.fullyQualifiedName);
    }

    public static boolean isNotBooleanOrBoxedBoolean(ParameterizedType parameterizedType) {
        if (parameterizedType.typeInfo == null) return true; // for parameterized types
        return !isBoolean(parameterizedType.typeInfo) && !isBoxedBoolean(parameterizedType.typeInfo);
    }

    public final TypeInfo longTypeInfo = new TypeInfo(JAVA_PRIMITIVE, "long");
    public final ParameterizedType longParameterizedType = longTypeInfo.asSimpleParameterizedType();

    public static boolean isLong(TypeInfo typeInfo) {
        return "long".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo boxedLongTypeInfo = new TypeInfo(JAVA_LANG, "Long");

    public static boolean isBoxedLong(TypeInfo typeInfo) {
        return "java.lang.Long".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo shortTypeInfo = new TypeInfo(JAVA_PRIMITIVE, "short");
    public final ParameterizedType shortParameterizedType = shortTypeInfo.asSimpleParameterizedType();

    public static boolean isShort(TypeInfo typeInfo) {
        return "short".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo boxedShortTypeInfo = new TypeInfo(JAVA_LANG, "Short");

    public static boolean isBoxedShort(TypeInfo typeInfo) {
        return "java.lang.Short".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo byteTypeInfo = new TypeInfo(JAVA_PRIMITIVE, "byte");
    public final ParameterizedType byteParameterizedType = byteTypeInfo.asSimpleParameterizedType();

    public static boolean isByte(TypeInfo typeInfo) {
        return "byte".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo boxedByteTypeInfo = new TypeInfo(JAVA_LANG, "Byte");

    public static boolean isBoxedByte(TypeInfo typeInfo) {
        return "java.lang.Byte".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo doubleTypeInfo = new TypeInfo(JAVA_PRIMITIVE, "double");
    public final ParameterizedType doubleParameterizedType = doubleTypeInfo.asSimpleParameterizedType();

    public static boolean isDouble(TypeInfo typeInfo) {
        return "double".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo boxedDoubleTypeInfo = new TypeInfo(JAVA_LANG, "Double");

    public static boolean isBoxedDouble(TypeInfo typeInfo) {
        return "java.lang.Double".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo floatTypeInfo = new TypeInfo(JAVA_PRIMITIVE, "float");
    public final ParameterizedType floatParameterizedType = floatTypeInfo.asSimpleParameterizedType();

    public static boolean isFloat(TypeInfo typeInfo) {
        return "float".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo boxedFloatTypeInfo = new TypeInfo(JAVA_LANG, "Float");

    public static boolean isBoxedFloat(TypeInfo typeInfo) {
        return "java.lang.Float".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo voidTypeInfo = new TypeInfo(JAVA_PRIMITIVE, "void");
    public final ParameterizedType voidParameterizedType = voidTypeInfo.asSimpleParameterizedType();
    public final TypeInfo boxedVoidTypeInfo = new TypeInfo(JAVA_LANG, "Void");

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
    public final ParameterizedType stringParameterizedType = stringTypeInfo.asSimpleParameterizedType();

    public static boolean isJavaLangString(TypeInfo typeInfo) {
        return "java.lang.String".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo annotationTypeTypeInfo = new TypeInfo(ORG_E2IMMU_ANNOTATION, "AnnotationType");
    private final ParameterizedType annotationTypePt = annotationTypeTypeInfo.asSimpleParameterizedType();
    public final FieldInfo annotationTypeComputed = new FieldInfo(annotationTypePt, "COMPUTED", annotationTypeTypeInfo);
    public final FieldInfo annotationTypeVerify = new FieldInfo(annotationTypePt, "VERIFY", annotationTypeTypeInfo);
    public final FieldInfo annotationTypeVerifyAbsent = new FieldInfo(annotationTypePt, "VERIFY_ABSENT", annotationTypeTypeInfo);
    public final FieldInfo annotationTypeContract = new FieldInfo(annotationTypePt, "CONTRACT", annotationTypeTypeInfo);
    public final FieldInfo annotationTypeContractAbsent = new FieldInfo(annotationTypePt, "CONTRACT_ABSENT", annotationTypeTypeInfo);

    public final TypeInfo annotationModeTypeInfo = new TypeInfo(ORG_E2IMMU_ANNOTATION, "AnnotationMode");
    public final FieldInfo annotationModeDefensive = new FieldInfo(annotationTypePt, "DEFENSIVE", annotationModeTypeInfo);
    public final FieldInfo annotationModeOffensive = new FieldInfo(annotationTypePt, "OFFENSIVE", annotationModeTypeInfo);

    public final TypeInfo functionalInterface = new TypeInfo(JAVA_LANG, "FunctionalInterface");
    public final AnnotationExpression functionalInterfaceAnnotationExpression =
            new AnnotationExpressionImpl(functionalInterface, List.of());

    public static boolean isFunctionalInterfaceAnnotation(TypeInfo typeInfo) {
        return "java.lang.FunctionalInterface".equals(typeInfo.fullyQualifiedName);
    }

    public final TypeInfo classTypeInfo = new TypeInfo(JAVA_LANG, "Class");

    public final TypeInfo objectTypeInfo = new TypeInfo(JAVA_LANG, "Object");
    public final ParameterizedType objectParameterizedType = objectTypeInfo.asSimpleParameterizedType();

    public static boolean isJavaLangObject(TypeInfo typeInfo) {
        return JAVA_LANG_OBJECT.equals(typeInfo.fullyQualifiedName);
    }

    public static boolean isJavaLangObject(ParameterizedType parameterizedType) {
        return parameterizedType.typeInfo != null && isJavaLangObject(parameterizedType.typeInfo);
    }

    public static boolean needsParent(TypeInfo typeInfo) {
        return typeInfo.fullyQualifiedName.indexOf('.') > 0 &&
                !typeInfo.fullyQualifiedName.startsWith("java.lang") &&
                !typeInfo.fullyQualifiedName.startsWith("jdk.internal");
    }

    public static boolean isNotJavaLang(TypeInfo typeInfo) {
        return typeInfo == null || !typeInfo.fullyQualifiedName.startsWith("java.lang.");
    }

    public static boolean isPostfix(MethodInfo operator) {
        return (operator.name.equals("++") || operator.name.equals("--")) && operator.returnType().typeInfo != null &&
                operator.returnType().typeInfo.fullyQualifiedName.equals(LONG_FQN);
    }

    private MethodInfo createOperator(TypeInfo owner, String name, List<ParameterizedType> parameterizedTypes, ParameterizedType returnType) {
        int i = 0;
        MethodInspectionImpl.Builder builder = new MethodInspectionImpl.Builder(owner, name).setStatic(true);
        for (ParameterizedType parameterizedType : parameterizedTypes) {
            ParameterInspectionImpl.Builder pb = new ParameterInspectionImpl.Builder(parameterizedType, "p" + i, i++);
            builder.addParameter(pb); // inspection built when method is built
        }
        builder.setReturnType(returnType);
        return builder.build(InspectionProvider.DEFAULT).getMethodInfo();
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

    // TODO long instead of int to distinguish statically (isPostfix) This is a hack!
    public final MethodInfo postfixIncrementOperatorInt = createOperator(intTypeInfo, "++", List.of(), longParameterizedType);
    public final MethodInfo prefixIncrementOperatorInt = createOperator(intTypeInfo, "++", List.of(), intParameterizedType);
    public final MethodInfo postfixDecrementOperatorInt = createOperator(intTypeInfo, "--", List.of(), longParameterizedType);
    public final MethodInfo prefixDecrementOperatorInt = createOperator(intTypeInfo, "--", List.of(), intParameterizedType);

    public final MethodInfo unaryPlusOperatorInt = createOperator(intTypeInfo, "+", List.of(intParameterizedType), intParameterizedType);
    public final MethodInfo unaryMinusOperatorInt = createOperator(intTypeInfo, "-", List.of(intParameterizedType), intParameterizedType);

    public static boolean isUnaryMinusOperatorInt(MethodInfo operator) {
        return UNARY_MINUS_OPERATOR_INT.equals(operator.fullyQualifiedName()) && operator.methodInspection.get().getParameters().size() == 1;
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
            ti.typeInspection.set(new TypeInspectionImpl.Builder(ti, BY_HAND)
                    .setTypeNature(TypeNature.PRIMITIVE)
                    .setParentClass(objectParameterizedType)
                    .build());
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

        processEnum(annotationTypeTypeInfo, List.of(annotationTypeComputed, annotationTypeContract, annotationTypeContractAbsent,
                annotationTypeVerify, annotationTypeVerifyAbsent));
        processEnum(annotationModeTypeInfo, List.of(annotationModeDefensive, annotationModeOffensive));

        functionalInterface.typeInspection.set(new TypeInspectionImpl.Builder(functionalInterface, BY_HAND)
                .setTypeNature(TypeNature.ANNOTATION)
                .setParentClass(objectParameterizedType)
                .build());

        assert UNARY_MINUS_OPERATOR_INT.equals(unaryMinusOperatorInt.fullyQualifiedName);
        assert LONG_FQN.equals(longTypeInfo.fullyQualifiedName): "Have "+longTypeInfo.fullyQualifiedName;
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
        MethodInspectionImpl.Builder valueOfBuilder = new MethodInspectionImpl.Builder(typeInfo, "valueOf").setStatic(true);
        ParameterInspectionImpl.Builder valueOf0Builder = new ParameterInspectionImpl.Builder(stringParameterizedType, "s", 0);
        ParameterizedType typeInfoAsPt = typeInfo.asSimpleParameterizedType();
        MethodInfo valueOf = valueOfBuilder.setReturnType(typeInfoAsPt)
                .addParameter(valueOf0Builder)
                .addModifier(MethodModifier.PUBLIC)
                .build(InspectionProvider.DEFAULT).getMethodInfo();

        MethodInspectionImpl.Builder nameBuilder = new MethodInspectionImpl.Builder(typeInfo, "name");
        MethodInfo name = nameBuilder.setReturnType(stringParameterizedType)
                .addModifier(MethodModifier.PUBLIC)
                .build(InspectionProvider.DEFAULT).getMethodInfo();
        TypeInspectionImpl.Builder typeInspectionBuilder = new TypeInspectionImpl.Builder(typeInfo, BY_HAND)
                .setTypeNature(TypeNature.ENUM)
                .addTypeModifier(TypeModifier.PUBLIC)
                .setParentClass(objectParameterizedType)
                .addMethod(valueOf)
                .addMethod(name);
        for (FieldInfo fieldInfo : fields) typeInspectionBuilder.addField(fieldInfo);
        typeInfo.typeInspection.set(typeInspectionBuilder.build());
        for (FieldInfo fieldInfo : fields) {
            fieldInfo.fieldInspection.set(new FieldInspectionImpl.Builder()
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

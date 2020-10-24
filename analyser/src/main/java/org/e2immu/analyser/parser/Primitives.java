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
    public static final Primitives PRIMITIVES = new Primitives();

    public static final String JAVA_LANG = "java.lang";
    public static final String JAVA_LANG_OBJECT = "java.lang.Object";

    public static final String ORG_E2IMMU_ANNOTATION = "org.e2immu.annotation";

    public final TypeInfo intTypeInfo = new TypeInfo("int");
    public final ParameterizedType intParameterizedType = intTypeInfo.asParameterizedType();
    public final TypeInfo integerTypeInfo = new TypeInfo("java.lang.Integer");

    public final TypeInfo charTypeInfo = new TypeInfo("char");
    public final ParameterizedType charParameterizedType = charTypeInfo.asParameterizedType();
    public final TypeInfo characterTypeInfo = new TypeInfo("java.lang.Character");

    public final TypeInfo booleanTypeInfo = new TypeInfo("boolean");
    public final ParameterizedType booleanParameterizedType = booleanTypeInfo.asParameterizedType();
    public final TypeInfo boxedBooleanTypeInfo = new TypeInfo("java.lang.Boolean");

    public final TypeInfo longTypeInfo = new TypeInfo("long");
    public final ParameterizedType longParameterizedType = longTypeInfo.asParameterizedType();
    public final TypeInfo boxedLongTypeInfo = new TypeInfo("java.lang.Long");

    public final TypeInfo shortTypeInfo = new TypeInfo("short");
    public final ParameterizedType shortParameterizedType = shortTypeInfo.asParameterizedType();
    public final TypeInfo boxedShortTypeInfo = new TypeInfo("java.lang.Short");

    public final TypeInfo byteTypeInfo = new TypeInfo("byte");
    public final ParameterizedType byteParameterizedType = byteTypeInfo.asParameterizedType();
    public final TypeInfo boxedByteTypeInfo = new TypeInfo("java.lang.Byte");

    public final TypeInfo doubleTypeInfo = new TypeInfo("double");
    public final ParameterizedType doubleParameterizedType = doubleTypeInfo.asParameterizedType();
    public final TypeInfo boxedDoubleTypeInfo = new TypeInfo("java.lang.Double");

    public final TypeInfo floatTypeInfo = new TypeInfo("float");
    public final ParameterizedType floatParameterizedType = floatTypeInfo.asParameterizedType();
    public final TypeInfo boxedFloatTypeInfo = new TypeInfo("java.lang.Float");

    public final TypeInfo voidTypeInfo = new TypeInfo("void");
    public final ParameterizedType voidParameterizedType = voidTypeInfo.asParameterizedType();
    public final TypeInfo boxedVoidTypeInfo = new TypeInfo("java.lang.Void");

    public final TypeInfo stringTypeInfo = new TypeInfo(JAVA_LANG, "String");
    public final ParameterizedType stringParameterizedType = stringTypeInfo.asParameterizedType();

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

    public final TypeInfo classTypeInfo = new TypeInfo(JAVA_LANG, "Class");

    public final TypeInfo objectTypeInfo = new TypeInfo(JAVA_LANG, "Object");
    public final ParameterizedType objectParameterizedType = objectTypeInfo.asParameterizedType();

    public final MethodInfo plusOperatorInt = new MethodInfo(intTypeInfo, "+",
            List.of(), intParameterizedType, true);

    public final MethodInfo minusOperatorInt = new MethodInfo(intTypeInfo, "-",
            List.of(), intParameterizedType, true);
    public final MethodInfo bitwiseOrOperatorInt = new MethodInfo(intTypeInfo, "|",
            List.of(), intParameterizedType, true);

    public final MethodInfo bitwiseAndOperatorInt = new MethodInfo(intTypeInfo, "&",
            List.of(), intParameterizedType, true);

    public final MethodInfo bitwiseXorOperatorInt = new MethodInfo(intTypeInfo, "^",
            List.of(), intParameterizedType, true);
    public final MethodInfo remainderOperatorInt = new MethodInfo(intTypeInfo, "%",
            List.of(), intParameterizedType, true);

    public final MethodInfo signedRightShiftOperatorInt = new MethodInfo(intTypeInfo, ">>",
            List.of(), intParameterizedType, true);
    public final MethodInfo unsignedRightShiftOperatorInt = new MethodInfo(intTypeInfo, ">>>",
            List.of(), intParameterizedType, true);
    public final MethodInfo leftShiftOperatorInt = new MethodInfo(intTypeInfo, "<<",
            List.of(), intParameterizedType, true);

    public final MethodInfo divideOperatorInt = new MethodInfo(intTypeInfo, "/",
            List.of(), intParameterizedType, true);

    public final MethodInfo equalsOperatorInt = new MethodInfo(intTypeInfo, "==",
            List.of(), booleanParameterizedType, true);
    public final MethodInfo notEqualsOperatorInt = new MethodInfo(intTypeInfo, "!=",
            List.of(), booleanParameterizedType, true);
    public final MethodInfo greaterOperatorInt = new MethodInfo(intTypeInfo, ">",
            List.of(), booleanParameterizedType, true);
    public final MethodInfo greaterEqualsOperatorInt = new MethodInfo(intTypeInfo, ">=",
            List.of(), booleanParameterizedType, true);
    public final MethodInfo lessOperatorInt = new MethodInfo(intTypeInfo, "<",
            List.of(), booleanParameterizedType, true);
    public final MethodInfo lessEqualsOperatorInt = new MethodInfo(intTypeInfo, "<=",
            List.of(), booleanParameterizedType, true);

    public final MethodInfo multiplyOperatorInt = new MethodInfo(intTypeInfo, "*",
            List.of(), intParameterizedType, true);

    public final MethodInfo assignOperatorInt = new MethodInfo(intTypeInfo, "=",
            List.of(), intParameterizedType, true);

    public final MethodInfo assignPlusOperatorInt = new MethodInfo(intTypeInfo, "+=",
            List.of(), intParameterizedType, true);
    public final MethodInfo assignMinusOperatorInt = new MethodInfo(intTypeInfo, "-=",
            List.of(), intParameterizedType, true);
    public final MethodInfo assignMultiplyOperatorInt = new MethodInfo(intTypeInfo, "*=",
            List.of(), intParameterizedType, true);
    public final MethodInfo assignDivideOperatorInt = new MethodInfo(intTypeInfo, "/=",
            List.of(), intParameterizedType, true);

    public final MethodInfo assignOrOperatorBoolean = new MethodInfo(intTypeInfo, "|=",
            List.of(), booleanParameterizedType, true);

    public final MethodInfo postfixIncrementOperatorInt = new MethodInfo(intTypeInfo, "++",
            List.of(), intParameterizedType, true);
    public final MethodInfo prefixIncrementOperatorInt = new MethodInfo(intTypeInfo, "++",
            List.of(), intParameterizedType, true);
    public final MethodInfo postfixDecrementOperatorInt = new MethodInfo(intTypeInfo, "--",
            List.of(), intParameterizedType, true);
    public final MethodInfo prefixDecrementOperatorInt = new MethodInfo(intTypeInfo, "--",
            List.of(), intParameterizedType, true);
    public final MethodInfo unaryPlusOperatorInt = new MethodInfo(intTypeInfo, "+",
            List.of(), intParameterizedType, true);
    public final MethodInfo unaryMinusOperatorInt = new MethodInfo(intTypeInfo, "-",
            List.of(), intParameterizedType, true);

    public final MethodInfo bitWiseNotOperatorInt = new MethodInfo(intTypeInfo, "~",
            List.of(), intParameterizedType, true);
    public final MethodInfo logicalNotOperatorBool = new MethodInfo(booleanTypeInfo, "!",
            List.of(), booleanParameterizedType, true);
    public final MethodInfo orOperatorBool = new MethodInfo(booleanTypeInfo, "||",
            List.of(), booleanParameterizedType, true);
    public final MethodInfo andOperatorBool = new MethodInfo(booleanTypeInfo, "&&",
            List.of(), booleanParameterizedType, true);

    public final MethodInfo plusOperatorString = new MethodInfo(stringTypeInfo, "+",
            List.of(), stringParameterizedType, true);

    public final MethodInfo equalsOperatorObject = new MethodInfo(objectTypeInfo, "==",
            List.of(), booleanParameterizedType, true);
    public final MethodInfo notEqualsOperatorObject = new MethodInfo(objectTypeInfo, "!=",
            List.of(), booleanParameterizedType, true);

    public final Map<String, TypeInfo> primitiveByName = new HashMap<>();
    public final Map<String, TypeInfo> typeByName = new HashMap<>();

    public final Set<TypeInfo> boxed = Set.of(boxedBooleanTypeInfo, boxedByteTypeInfo, boxedDoubleTypeInfo, boxedFloatTypeInfo,
            boxedLongTypeInfo, boxedShortTypeInfo, boxedVoidTypeInfo, integerTypeInfo, characterTypeInfo);

    public final Set<TypeInfo> primitives = Set.of(booleanTypeInfo, byteTypeInfo, doubleTypeInfo, floatTypeInfo,
            longTypeInfo, shortTypeInfo, voidTypeInfo, intTypeInfo, charTypeInfo);

    public final Set<TypeInfo> numericPrimitives = Set.of(shortTypeInfo, intTypeInfo, doubleTypeInfo, floatTypeInfo, byteTypeInfo, longTypeInfo);

    public final Set<TypeInfo> numericBoxed = Set.of(boxedShortTypeInfo, integerTypeInfo, boxedDoubleTypeInfo,
            boxedFloatTypeInfo, boxedLongTypeInfo, boxedByteTypeInfo);


    public Primitives() {
        for (TypeInfo ti : primitives) {
            ti.typeInspection.set(new TypeInspection.TypeInspectionBuilder()
                    .setPackageName(JAVA_LANG)
                    .setTypeNature(TypeNature.PRIMITIVE)
                    .build(false, ti));
            primitiveByName.put(ti.simpleName, ti);
            TypeAnalysisImpl.Builder builder = new TypeAnalysisImpl.Builder(ti);
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
                .build(false, functionalInterface));
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

                .addMethod(valueOf)
                .addMethod(name);
        for (FieldInfo fieldInfo : fields) typeInspectionBuilder.addField(fieldInfo);
        typeInfo.typeInspection.set(typeInspectionBuilder.build(false, typeInfo));
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
}

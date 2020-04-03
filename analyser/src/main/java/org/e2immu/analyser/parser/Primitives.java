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

import org.e2immu.analyser.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Primitives {
    public static final Primitives PRIMITIVES = new Primitives();

    public static final String JAVA_LANG = "java.lang";
    public static final String JAVA_LANG_OBJECT = "java.lang.Object";

    public static final String ORG_E2IMMU_ANNOTATION = "org.e2immu.annotation";

    public final TypeInfo intTypeInfo = new TypeInfo("int");
    public final ParameterizedType intParameterizedType = intTypeInfo.asParameterizedType();

    public final TypeInfo charTypeInfo = new TypeInfo("char");
    public final ParameterizedType charParameterizedType = charTypeInfo.asParameterizedType();

    public final TypeInfo booleanTypeInfo = new TypeInfo("boolean");
    public final ParameterizedType booleanParameterizedType = booleanTypeInfo.asParameterizedType();

    public final TypeInfo longTypeInfo = new TypeInfo("long");
    public final ParameterizedType longParameterizedType = longTypeInfo.asParameterizedType();

    public final TypeInfo shortTypeInfo = new TypeInfo("short");
    public final ParameterizedType shortParameterizedType = shortTypeInfo.asParameterizedType();

    public final TypeInfo byteTypeInfo = new TypeInfo("byte");
    public final ParameterizedType byteParameterizedType = byteTypeInfo.asParameterizedType();

    public final TypeInfo doubleTypeInfo = new TypeInfo("double");
    public final ParameterizedType doubleParameterizedType = doubleTypeInfo.asParameterizedType();

    public final TypeInfo floatTypeInfo = new TypeInfo("float");
    public final ParameterizedType floatParameterizedType = floatTypeInfo.asParameterizedType();


    public final TypeInfo voidTypeInfo = new TypeInfo("void");
    public final ParameterizedType voidParameterizedType = voidTypeInfo.asParameterizedType();

    public final TypeInfo stringTypeInfo = new TypeInfo(JAVA_LANG, "String");
    public final ParameterizedType stringParameterizedType = stringTypeInfo.asParameterizedType();

    public final TypeInfo annotationTypeTypeInfo = new TypeInfo(ORG_E2IMMU_ANNOTATION, "AnnotationType");
    public final FieldInfo annotationTypeComputed = new FieldInfo(annotationTypeTypeInfo, "COMPUTED", annotationTypeTypeInfo);
    public final FieldInfo annotationTypeVerify = new FieldInfo(annotationTypeTypeInfo, "VERIFY", annotationTypeTypeInfo);
    public final FieldInfo annotationTypeVerifyAbsent = new FieldInfo(annotationTypeTypeInfo, "VERIFY_ABSENT", annotationTypeTypeInfo);
    public final FieldInfo annotationTypeContract = new FieldInfo(annotationTypeTypeInfo, "CONTRACT", annotationTypeTypeInfo);

    public final TypeInfo classTypeInfo = new TypeInfo(JAVA_LANG, "Class");

    public final TypeInfo objectTypeInfo = new TypeInfo(JAVA_LANG, "Object");
    public final ParameterizedType objectParameterizedType = objectTypeInfo.asParameterizedType();

    public final MethodInfo plusOperatorInt = new MethodInfo(intTypeInfo, "+",
            List.of(new ParameterInfo(intParameterizedType, "lhs", 0),
                    new ParameterInfo(intParameterizedType, "rhs", 1)), intParameterizedType, true);

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

    public final MethodInfo divideOperatorInt = new MethodInfo(intTypeInfo, "/",
            List.of(), intParameterizedType, true);

    public final MethodInfo greaterOperatorInt = new MethodInfo(intTypeInfo, ">",
            List.of(), booleanParameterizedType, true);
    public final MethodInfo greaterEqualsOperatorInt = new MethodInfo(intTypeInfo, ">=",
            List.of(), booleanParameterizedType, true);
    public final MethodInfo lessOperatorInt = new MethodInfo(intTypeInfo, "<",
            List.of(), booleanParameterizedType, true);
    public final MethodInfo lessEqualsOperatorInt = new MethodInfo(intTypeInfo, "<=",
            List.of(), booleanParameterizedType, true);

    public final MethodInfo multiplyOperatorInt = new MethodInfo(intTypeInfo, "*",
            List.of(new ParameterInfo(intParameterizedType, "lhs", 0),
                    new ParameterInfo(intParameterizedType, "rhs", 1)), intParameterizedType, true);

    public final MethodInfo assignOperatorInt = new MethodInfo(intTypeInfo, "=",
            List.of(), intParameterizedType, true);

    public final MethodInfo assignPlusOperatorInt = new MethodInfo(intTypeInfo, "+=",
            List.of(), intParameterizedType, true);

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

    public Primitives() {
        for (TypeInfo ti : List.of(intTypeInfo, charTypeInfo, booleanTypeInfo,
                longTypeInfo, shortTypeInfo, byteTypeInfo, doubleTypeInfo, floatTypeInfo, voidTypeInfo)) {
            ti.typeInspection.set(new TypeInspection.TypeInspectionBuilder()
                    .setPackageName(JAVA_LANG)
                    .setTypeNature(TypeNature.PRIMITIVE)
                    .build(false, ti));
            primitiveByName.put(ti.simpleName, ti);
        }

        for (TypeInfo ti : List.of(stringTypeInfo, objectTypeInfo, classTypeInfo, annotationTypeTypeInfo)) {
            typeByName.put(ti.simpleName, ti);
        }

        annotationTypeTypeInfo.typeInspection.set(new TypeInspection.TypeInspectionBuilder()
                .setPackageName(ORG_E2IMMU_ANNOTATION)
                .setTypeNature(TypeNature.ENUM)
                .addField(annotationTypeComputed)
                .addField(annotationTypeContract)
                .addField(annotationTypeVerify)
                .addField(annotationTypeVerifyAbsent)
                .build(true, annotationTypeTypeInfo));
        for (FieldInfo fieldInfo : new FieldInfo[]{annotationTypeComputed, annotationTypeContract, annotationTypeVerify, annotationTypeVerifyAbsent}) {
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
}

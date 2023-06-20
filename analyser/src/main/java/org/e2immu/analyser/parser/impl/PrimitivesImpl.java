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

package org.e2immu.analyser.parser.impl;

import org.e2immu.analyser.analyser.AnalysisProvider;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.SetOfTypes;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.impl.MethodAnalysisImpl;
import org.e2immu.analyser.analysis.impl.ParameterAnalysisImpl;
import org.e2immu.analyser.analysis.impl.TypeAnalysisImpl;
import org.e2immu.analyser.inspector.impl.FieldInspectionImpl;
import org.e2immu.analyser.inspector.impl.MethodInspectionImpl;
import org.e2immu.analyser.inspector.impl.ParameterInspectionImpl;
import org.e2immu.analyser.inspector.impl.TypeInspectionImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.impl.AnnotationExpressionImpl;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;

import java.util.*;
import java.util.stream.Collectors;

import static org.e2immu.analyser.analysis.Analysis.AnalysisMode.CONTRACTED;
import static org.e2immu.analyser.inspector.InspectionState.BY_HAND_WITHOUT_STATEMENTS;
import static org.e2immu.analyser.model.IsAssignableFrom.NOT_ASSIGNABLE;

public class PrimitivesImpl implements Primitives {
    public static final String ANNOTATION_TYPE = "annotation type";
    public final TypeInfo intTypeInfo = new TypeInfo(JAVA_PRIMITIVE, "int");
    public final ParameterizedType intParameterizedType = intTypeInfo.asSimpleParameterizedType();

    public final TypeInfo integerTypeInfo = new TypeInfo(JAVA_LANG, "Integer");

    public final TypeInfo charTypeInfo = new TypeInfo(JAVA_PRIMITIVE, "char");
    public final ParameterizedType charParameterizedType = charTypeInfo.asSimpleParameterizedType();

    public final TypeInfo characterTypeInfo = new TypeInfo(JAVA_LANG, "Character");

    public final TypeInfo booleanTypeInfo = new TypeInfo(JAVA_PRIMITIVE, "boolean");
    public final ParameterizedType booleanParameterizedType = booleanTypeInfo.asSimpleParameterizedType();

    public final TypeInfo boxedBooleanTypeInfo = new TypeInfo(JAVA_LANG, "Boolean");
    public final ParameterizedType boxedBooleanParameterizedType = boxedBooleanTypeInfo.asSimpleParameterizedType();

    public final TypeInfo longTypeInfo = new TypeInfo(JAVA_PRIMITIVE, "long");
    public final ParameterizedType longParameterizedType = longTypeInfo.asSimpleParameterizedType();

    public final TypeInfo boxedLongTypeInfo = new TypeInfo(JAVA_LANG, "Long");

    public final TypeInfo shortTypeInfo = new TypeInfo(JAVA_PRIMITIVE, "short");
    public final ParameterizedType shortParameterizedType = shortTypeInfo.asSimpleParameterizedType();

    public final TypeInfo boxedShortTypeInfo = new TypeInfo(JAVA_LANG, "Short");

    public final TypeInfo byteTypeInfo = new TypeInfo(JAVA_PRIMITIVE, "byte");
    public final ParameterizedType byteParameterizedType = byteTypeInfo.asSimpleParameterizedType();

    public final TypeInfo boxedByteTypeInfo = new TypeInfo(JAVA_LANG, "Byte");

    public final TypeInfo doubleTypeInfo = new TypeInfo(JAVA_PRIMITIVE, "double");
    public final ParameterizedType doubleParameterizedType = doubleTypeInfo.asSimpleParameterizedType();

    public final TypeInfo boxedDoubleTypeInfo = new TypeInfo(JAVA_LANG, "Double");

    public final TypeInfo floatTypeInfo = new TypeInfo(JAVA_PRIMITIVE, "float");
    public final ParameterizedType floatParameterizedType = floatTypeInfo.asSimpleParameterizedType();

    public final TypeInfo boxedFloatTypeInfo = new TypeInfo(JAVA_LANG, "Float");

    public final TypeInfo voidTypeInfo = new TypeInfo(JAVA_PRIMITIVE, "void");
    public final ParameterizedType voidParameterizedType = voidTypeInfo.asSimpleParameterizedType();
    public final TypeInfo boxedVoidTypeInfo = new TypeInfo(JAVA_LANG, "Void");

    public final TypeInfo stringTypeInfo = new TypeInfo(JAVA_LANG, "String");
    public final ParameterizedType stringParameterizedType = stringTypeInfo.asSimpleParameterizedType();

    public ParameterizedType stringParameterizedType() {
        return stringParameterizedType;
    }

    @Override
    public ParameterizedType intParameterizedType() {
        return intParameterizedType;
    }

    @Override
    public ParameterizedType booleanParameterizedType() {
        return booleanParameterizedType;
    }

    @Override
    public ParameterizedType boxedBooleanParameterizedType() {
        return boxedBooleanParameterizedType;
    }

    @Override
    public ParameterizedType longParameterizedType() {
        return longParameterizedType;
    }

    @Override
    public ParameterizedType doubleParameterizedType() {
        return doubleParameterizedType;
    }

    @Override
    public ParameterizedType floatParameterizedType() {
        return floatParameterizedType;
    }

    @Override
    public ParameterizedType shortParameterizedType() {
        return shortParameterizedType;
    }

    @Override
    public ParameterizedType charParameterizedType() {
        return charParameterizedType;
    }

    @Override
    public MethodInfo createOperator(TypeInfo owner,
                                     String name,
                                     List<ParameterizedType> parameterizedTypes,
                                     ParameterizedType returnType) {
        int i = 0;
        MethodInspection.Builder builder = new MethodInspectionImpl.Builder(owner, name).setStatic(true);
        for (ParameterizedType parameterizedType : parameterizedTypes) {
            ParameterInspectionImpl.Builder pb = new ParameterInspectionImpl.Builder(Identifier.generate("operator param"),
                    parameterizedType, "p" + i, i++);
            builder.addParameter(pb); // inspection built when method is built
        }
        builder.setReturnType(returnType);
        builder.setAccess(Inspection.Access.PUBLIC);
        return builder.build(InspectionProvider.DEFAULT).getMethodInfo();
    }

    public final TypeInfo annotationTypeTypeInfo = new TypeInfo(ORG_E2IMMU_ANNOTATION, "AnnotationType");
    private final ParameterizedType annotationTypePt = annotationTypeTypeInfo.asSimpleParameterizedType();
    public final FieldInfo annotationTypeComputed = new FieldInfo(Identifier.generate(ANNOTATION_TYPE),
            annotationTypePt, "COMPUTED", annotationTypeTypeInfo);
    public final FieldInfo annotationTypeVerify = new FieldInfo(Identifier.generate(ANNOTATION_TYPE),
            annotationTypePt, "VERIFY", annotationTypeTypeInfo);
    public final FieldInfo annotationTypeVerifyAbsent = new FieldInfo(Identifier.generate(ANNOTATION_TYPE),
            annotationTypePt, "VERIFY_ABSENT", annotationTypeTypeInfo);
    public final FieldInfo annotationTypeContract = new FieldInfo(Identifier.generate(ANNOTATION_TYPE),
            annotationTypePt, "CONTRACT", annotationTypeTypeInfo);
    public final FieldInfo annotationTypeContractAbsent = new FieldInfo(Identifier.generate(ANNOTATION_TYPE),
            annotationTypePt, "CONTRACT_ABSENT", annotationTypeTypeInfo);

    public final TypeInfo annotationModeTypeInfo = new TypeInfo(ORG_E2IMMU_ANNOTATION, "AnnotationMode");
    public final FieldInfo annotationModeDefensive = new FieldInfo(Identifier.generate("annotation mode"),
            annotationTypePt, "DEFENSIVE", annotationModeTypeInfo);
    public final FieldInfo annotationModeOffensive = new FieldInfo(Identifier.generate("annotation mode"),
            annotationTypePt, "OFFENSIVE", annotationModeTypeInfo);

    public final TypeInfo functionalInterface = new TypeInfo(JAVA_LANG, "FunctionalInterface");
    public final AnnotationExpression functionalInterfaceAnnotationExpression =
            new AnnotationExpressionImpl(functionalInterface, List.of());

    public final TypeInfo classTypeInfo = new TypeInfo(JAVA_LANG, "Class");

    public final TypeInfo objectTypeInfo = new TypeInfo(JAVA_LANG, "Object");
    public final ParameterizedType objectParameterizedType = objectTypeInfo.asSimpleParameterizedType();

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
    public final MethodInfo assignOrOperatorInt = createOperator(intTypeInfo, "|=", List.of(intParameterizedType), intParameterizedType);
    public final MethodInfo assignAndOperatorInt = createOperator(intTypeInfo, "&=", List.of(intParameterizedType), intParameterizedType);

    // TODO long instead of int to distinguish statically (isPostfix) This is a hack!
    public final MethodInfo postfixIncrementOperatorInt = createOperator(intTypeInfo, "++", List.of(), longParameterizedType);
    public final MethodInfo prefixIncrementOperatorInt = createOperator(intTypeInfo, "++", List.of(), intParameterizedType);
    public final MethodInfo postfixDecrementOperatorInt = createOperator(intTypeInfo, "--", List.of(), longParameterizedType);
    public final MethodInfo prefixDecrementOperatorInt = createOperator(intTypeInfo, "--", List.of(), intParameterizedType);

    public final MethodInfo unaryPlusOperatorInt = createOperator(intTypeInfo, "+", List.of(intParameterizedType), intParameterizedType);
    public final MethodInfo unaryMinusOperatorInt = createOperator(intTypeInfo, "-", List.of(intParameterizedType), intParameterizedType);

    public final MethodInfo bitWiseNotOperatorInt = createOperator(intTypeInfo, "~", List.of(intParameterizedType), intParameterizedType);
    public final MethodInfo logicalNotOperatorBool = createOperator(booleanTypeInfo, "!", List.of(booleanParameterizedType), booleanParameterizedType);
    public final MethodInfo orOperatorBool = createOperator(booleanTypeInfo, "||", boolBool, booleanParameterizedType);
    public final MethodInfo andOperatorBool = createOperator(booleanTypeInfo, "&&", boolBool, booleanParameterizedType);
    public final MethodInfo xorOperatorBool = createOperator(booleanTypeInfo, "^", boolBool, booleanParameterizedType);

    public final MethodInfo plusOperatorString = createOperator(stringTypeInfo, "+", List.of(stringParameterizedType,
            stringParameterizedType), stringParameterizedType);

    public final MethodInfo equalsOperatorObject = createOperator(objectTypeInfo, "==",
            List.of(objectParameterizedType, objectParameterizedType), booleanParameterizedType);
    public final MethodInfo notEqualsOperatorObject = createOperator(objectTypeInfo, "!=",
            List.of(objectParameterizedType, objectParameterizedType), booleanParameterizedType);

    public final Map<String, TypeInfo> primitiveByName = new HashMap<>();
    public final Map<String, TypeInfo> typeByName = new HashMap<>();

    public Map<String, TypeInfo> getTypeByName() {
        return typeByName;
    }

    public Map<String, TypeInfo> getPrimitiveByName() {
        return primitiveByName;
    }

    public final Set<TypeInfo> boxed = Set.of(boxedBooleanTypeInfo, boxedByteTypeInfo, boxedDoubleTypeInfo, boxedFloatTypeInfo,
            boxedLongTypeInfo, boxedShortTypeInfo, boxedVoidTypeInfo, integerTypeInfo, characterTypeInfo);

    public final Set<TypeInfo> primitives = Set.of(booleanTypeInfo, byteTypeInfo, doubleTypeInfo, floatTypeInfo,
            longTypeInfo, shortTypeInfo, voidTypeInfo, intTypeInfo, charTypeInfo);

    // normally, this information is read from the annotated APIs
    @Override
    public void setInspectionOfBoxedTypesForTesting() {
        for (TypeInfo ti : boxed) {
            ti.typeInspection.set(new TypeInspectionImpl.Builder(ti, BY_HAND_WITHOUT_STATEMENTS)
                    .setTypeNature(TypeNature.CLASS)
                    .setFunctionalInterface(false)
                    .noParent(this)
                    .setAccess(Inspection.Access.PUBLIC)
                    .build(null));
            TypeAnalysisImpl.Builder builder = new TypeAnalysisImpl.Builder(CONTRACTED, this, ti, null);
            builder.setProperty(Property.CONTAINER, MultiLevel.CONTAINER_DV);
            builder.setProperty(Property.IMMUTABLE, MultiLevel.EFFECTIVELY_IMMUTABLE_DV);
            builder.freezeApprovedPreconditionsImmutable(); // cannot change these anymore; will never be eventual
            builder.freezeApprovedPreconditionsFinalFields(); // cannot change these anymore; will never be eventual
            builder.setProperty(Property.INDEPENDENT, MultiLevel.INDEPENDENT_DV);
            builder.setHiddenContentTypes(SetOfTypes.EMPTY);
            builder.setImmutableDeterminedByTypeParameters(false);
            ti.typeAnalysis.set(builder.build());
        }
    }

    public PrimitivesImpl() {
        for (TypeInfo ti : primitives) {
            ti.typeInspection.set(new TypeInspectionImpl.Builder(ti, BY_HAND_WITHOUT_STATEMENTS)
                    .setTypeNature(TypeNature.PRIMITIVE)
                    .setAccess(Inspection.Access.PUBLIC)
                    .setFunctionalInterface(false)
                    .noParent(this)
                    .build(null));
            primitiveByName.put(ti.simpleName, ti);
            TypeAnalysisImpl.Builder builder = new TypeAnalysisImpl.Builder(CONTRACTED, this, ti, null);
            ti.typeAnalysis.set(builder);
            builder.setProperty(Property.CONTAINER, MultiLevel.CONTAINER_DV);
            builder.setProperty(Property.IMMUTABLE, MultiLevel.EFFECTIVELY_IMMUTABLE_DV);
            builder.setProperty(Property.INDEPENDENT, MultiLevel.INDEPENDENT_DV);
            builder.freezeApprovedPreconditionsImmutable(); // cannot change these anymore; will never be eventual
            builder.freezeApprovedPreconditionsFinalFields(); // cannot change these anymore; will never be eventual
            builder.setHiddenContentTypes(SetOfTypes.EMPTY);
            builder.setImmutableDeterminedByTypeParameters(false);
        }

        for (TypeInfo ti : List.of(stringTypeInfo, objectTypeInfo, classTypeInfo, annotationTypeTypeInfo,
                annotationModeTypeInfo, functionalInterface)) {
            typeByName.put(ti.simpleName, ti);
        }
        for (TypeInfo ti : boxed) {
            typeByName.put(ti.simpleName, ti);
        }

        processEnum(annotationTypeTypeInfo, List.of(annotationTypeComputed, annotationTypeContract, annotationTypeContractAbsent,
                annotationTypeVerify, annotationTypeVerifyAbsent));
        processEnum(annotationModeTypeInfo, List.of(annotationModeDefensive, annotationModeOffensive));

        functionalInterface.typeInspection.set(new TypeInspectionImpl.Builder(functionalInterface, BY_HAND_WITHOUT_STATEMENTS)
                .setTypeNature(TypeNature.ANNOTATION)
                .setAccess(Inspection.Access.PUBLIC)
                .setFunctionalInterface(false)
                .noParent(this)
                .build(null));

        assert MethodInfo.UNARY_MINUS_OPERATOR_INT.equals(unaryMinusOperatorInt.fullyQualifiedName);
        assert "long".equals(longTypeInfo.fullyQualifiedName) : "Have " + longTypeInfo.fullyQualifiedName;
    }

    @Override
    public void processEnum(TypeInfo typeInfo, List<FieldInfo> fields) {
        MethodInspection.Builder valueOfBuilder = new MethodInspectionImpl.Builder(typeInfo, "valueOf").setStatic(true);
        ParameterInspectionImpl.Builder valueOf0Builder =
                new ParameterInspectionImpl.Builder(Identifier.generate("param valueOf enum"), stringParameterizedType, "s", 0);
        ParameterizedType typeInfoAsPt = typeInfo.asSimpleParameterizedType();
        MethodInfo valueOf = valueOfBuilder.setReturnType(typeInfoAsPt)
                .addParameter(valueOf0Builder)
                .addModifier(MethodModifier.PUBLIC)
                .setAccess(Inspection.Access.PUBLIC)
                .build(InspectionProvider.DEFAULT).getMethodInfo();

        MethodInspectionImpl.Builder nameBuilder = new MethodInspectionImpl.Builder(typeInfo, "name");
        MethodInfo name = nameBuilder.setReturnType(stringParameterizedType)
                .addModifier(MethodModifier.PUBLIC)
                .setAccess(Inspection.Access.PUBLIC)
                .build(InspectionProvider.DEFAULT).getMethodInfo();
        TypeInspection.Builder typeInspectionBuilder = new TypeInspectionImpl.Builder(typeInfo, BY_HAND_WITHOUT_STATEMENTS)
                .setTypeNature(TypeNature.ENUM)
                .setAccess(Inspection.Access.PUBLIC)
                .addTypeModifier(TypeModifier.PUBLIC)
                .setFunctionalInterface(false)
                .noParent(this)
                .addMethod(valueOf)
                .addMethod(name);
        for (FieldInfo fieldInfo : fields) typeInspectionBuilder.addField(fieldInfo);
        typeInfo.typeInspection.set(typeInspectionBuilder.build(null));
        for (FieldInfo fieldInfo : fields) {
            fieldInfo.fieldInspection.set(new FieldInspectionImpl.Builder(fieldInfo)
                    .addModifiers(List.of(FieldModifier.STATIC, FieldModifier.FINAL, FieldModifier.PUBLIC))
                    .setAccess(Inspection.Access.PUBLIC)
                    .build(null));
        }
    }

    @Override
    public ParameterizedType widestType(ParameterizedType t1, ParameterizedType t2) {
        int o1 = primitiveTypeOrder(Objects.requireNonNull(t1));
        int o2 = primitiveTypeOrder(Objects.requireNonNull(t2));
        if (o1 >= o2) return t1;
        return t2;
    }

    @Override
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

    @Override
    public TypeInfo primitiveByName(String asString) {
        TypeInfo ti = primitiveByName.get(asString);
        if (ti == null) throw new UnsupportedOperationException("Type " + asString + " not (yet) a primitive");
        return ti;
    }

    @Override
    public int isAssignableFromTo(ParameterizedType from, ParameterizedType to, boolean covariant) {
        int fromOrder = primitiveTypeOrder(from);
        if (fromOrder <= 1 || fromOrder >= 9) return NOT_ASSIGNABLE;
        int toOrder = primitiveTypeOrder(to);
        if (toOrder <= 1 || toOrder >= 9) return NOT_ASSIGNABLE;
        int diff = covariant ? toOrder - fromOrder : fromOrder - toOrder;
        return diff < 0 ? NOT_ASSIGNABLE : diff;
    }

    @Override
    public boolean isPreOrPostFixOperator(MethodInfo operator) {
        return operator == postfixDecrementOperatorInt || // i--;
                operator == postfixIncrementOperatorInt || // i++;
                operator == prefixDecrementOperatorInt || // --i;
                operator == prefixIncrementOperatorInt; // ++i;
    }

    @Override
    public boolean isPrefixOperator(MethodInfo operator) {
        return operator == prefixDecrementOperatorInt || operator == prefixIncrementOperatorInt;
    }

    @Override
    public MethodInfo prePostFixToAssignment(MethodInfo operator) {
        if (operator == postfixDecrementOperatorInt || operator == prefixDecrementOperatorInt) {
            return assignMinusOperatorInt;
        }
        if (operator == postfixIncrementOperatorInt || operator == prefixIncrementOperatorInt) {
            return assignPlusOperatorInt;
        }
        throw new UnsupportedOperationException();
    }

    @Override
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
        if (typeInfo == voidTypeInfo) {
            return boxedVoidTypeInfo;
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public TypeInfo unboxed(TypeInfo typeInfo) {
        if (typeInfo == boxedLongTypeInfo)
            return longTypeInfo;
        if (typeInfo == integerTypeInfo)
            return intTypeInfo;
        if (typeInfo == boxedShortTypeInfo)
            return shortTypeInfo;
        if (typeInfo == boxedByteTypeInfo)
            return byteTypeInfo;
        if (typeInfo == characterTypeInfo)
            return charTypeInfo;
        if (typeInfo == boxedBooleanTypeInfo)
            return booleanTypeInfo;
        if (typeInfo == boxedFloatTypeInfo)
            return floatTypeInfo;
        if (typeInfo == boxedDoubleTypeInfo)
            return doubleTypeInfo;
        if (typeInfo == boxedVoidTypeInfo) {
            return voidTypeInfo;
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public SetOfTypes explicitTypesOfJLO() {
        return new SetOfTypes(Set.of(stringParameterizedType, objectParameterizedType,
                classTypeInfo.asSimpleParameterizedType()));
    }

    @Override
    public MethodInfo assignOperatorInt() {
        return assignOperatorInt;
    }

    @Override
    public MethodInfo assignPlusOperatorInt() {
        return assignPlusOperatorInt;
    }

    @Override
    public MethodInfo assignMinusOperatorInt() {
        return assignMinusOperatorInt;
    }

    @Override
    public MethodInfo assignMultiplyOperatorInt() {
        return assignMultiplyOperatorInt;
    }

    @Override
    public MethodInfo assignDivideOperatorInt() {
        return assignDivideOperatorInt;
    }

    @Override
    public MethodInfo assignOrOperatorInt() {
        return assignOrOperatorInt;
    }

    @Override
    public MethodInfo assignAndOperatorInt() {
        return assignAndOperatorInt;
    }

    @Override
    public MethodInfo plusOperatorInt() {
        return plusOperatorInt;
    }

    @Override
    public MethodInfo minusOperatorInt() {
        return minusOperatorInt;
    }

    @Override
    public MethodInfo multiplyOperatorInt() {
        return multiplyOperatorInt;
    }

    @Override
    public MethodInfo divideOperatorInt() {
        return divideOperatorInt;
    }

    @Override
    public MethodInfo bitwiseOrOperatorInt() {
        return bitwiseOrOperatorInt;
    }

    @Override
    public MethodInfo bitwiseAndOperatorInt() {
        return bitwiseAndOperatorInt;
    }

    @Override
    public MethodInfo orOperatorBool() {
        return orOperatorBool;
    }

    @Override
    public MethodInfo andOperatorBool() {
        return andOperatorBool;
    }

    @Override
    public MethodInfo equalsOperatorObject() {
        return equalsOperatorObject;
    }

    @Override
    public MethodInfo equalsOperatorInt() {
        return equalsOperatorInt;
    }

    @Override
    public MethodInfo notEqualsOperatorObject() {
        return notEqualsOperatorObject;
    }

    @Override
    public MethodInfo notEqualsOperatorInt() {
        return notEqualsOperatorInt;
    }

    @Override
    public MethodInfo plusOperatorString() {
        return plusOperatorString;
    }

    @Override
    public MethodInfo xorOperatorBool() {
        return xorOperatorBool;
    }

    @Override
    public MethodInfo bitwiseXorOperatorInt() {
        return bitwiseXorOperatorInt;
    }

    @Override
    public MethodInfo leftShiftOperatorInt() {
        return leftShiftOperatorInt;
    }

    @Override
    public MethodInfo signedRightShiftOperatorInt() {
        return signedRightShiftOperatorInt;
    }

    @Override
    public MethodInfo unsignedRightShiftOperatorInt() {
        return unsignedRightShiftOperatorInt;
    }

    @Override
    public MethodInfo greaterOperatorInt() {
        return greaterOperatorInt;
    }

    @Override
    public MethodInfo greaterEqualsOperatorInt() {
        return greaterEqualsOperatorInt;
    }

    @Override
    public MethodInfo lessEqualsOperatorInt() {
        return lessEqualsOperatorInt;
    }

    @Override
    public MethodInfo lessOperatorInt() {
        return lessOperatorInt;
    }

    @Override
    public MethodInfo remainderOperatorInt() {
        return remainderOperatorInt;
    }

    @Override
    public TypeInfo stringTypeInfo() {
        return stringTypeInfo;
    }

    @Override
    public TypeInfo booleanTypeInfo() {
        return booleanTypeInfo;
    }

    @Override
    public TypeInfo charTypeInfo() {
        return charTypeInfo;
    }

    @Override
    public ParameterizedType byteParameterizedType() {
        return byteParameterizedType;
    }

    @Override
    public TypeInfo classTypeInfo() {
        return classTypeInfo;
    }

    @Override
    public ParameterizedType objectParameterizedType() {
        return objectParameterizedType;
    }

    @Override
    public ParameterizedType voidParameterizedType() {
        return voidParameterizedType;
    }

    @Override
    public MethodInfo logicalNotOperatorBool() {
        return logicalNotOperatorBool;
    }

    @Override
    public MethodInfo unaryMinusOperatorInt() {
        return unaryMinusOperatorInt;
    }

    @Override
    public MethodInfo unaryPlusOperatorInt() {
        return unaryPlusOperatorInt;
    }

    @Override
    public MethodInfo prefixIncrementOperatorInt() {
        return prefixIncrementOperatorInt;
    }

    @Override
    public MethodInfo postfixIncrementOperatorInt() {
        return postfixIncrementOperatorInt;
    }

    @Override
    public MethodInfo prefixDecrementOperatorInt() {
        return prefixDecrementOperatorInt;
    }

    @Override
    public MethodInfo postfixDecrementOperatorInt() {
        return postfixDecrementOperatorInt;
    }

    @Override
    public MethodInfo bitWiseNotOperatorInt() {
        return bitWiseNotOperatorInt;
    }

    @Override
    public TypeInfo integerTypeInfo() {
        return integerTypeInfo;
    }

    @Override
    public TypeInfo intTypeInfo() {
        return intTypeInfo;
    }

    @Override
    public TypeInfo boxedBooleanTypeInfo() {
        return boxedBooleanTypeInfo;
    }

    @Override
    public TypeInfo characterTypeInfo() {
        return characterTypeInfo;
    }

    @Override
    public TypeInfo objectTypeInfo() {
        return objectTypeInfo;
    }

    @Override
    public AnnotationExpression functionalInterfaceAnnotationExpression() {
        return functionalInterfaceAnnotationExpression;
    }

    @Override
    public MethodAnalysis createEmptyMethodAnalysis(MethodInfo methodInfo) {
        List<ParameterAnalysis> parameterAnalyses = methodInfo.methodInspection.get().getParameters().stream()
                .map(p -> {
                    ParameterAnalysisImpl.Builder pb = new ParameterAnalysisImpl.Builder(this, AnalysisProvider.DEFAULT_PROVIDER, p);
                    pb.setProperty(Property.CONTAINER_RESTRICTION, MultiLevel.NOT_CONTAINER_DV);
                    return (ParameterAnalysis) pb.build();
                })
                .collect(Collectors.toList());
        MethodAnalysisImpl.Builder builder = new MethodAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED,
                this, AnalysisProvider.DEFAULT_PROVIDER, InspectionProvider.DEFAULT,
                methodInfo, null, parameterAnalyses);
        builder.ensureIsNotEventualUnlessOtherwiseAnnotated();
        return (MethodAnalysis) builder.build();
    }

    @Override
    public MethodInfo assignOperator(ParameterizedType returnType) {
        // NOTE: we have only one at the moment, no distinction between the types
        return assignOperatorInt;
    }
}

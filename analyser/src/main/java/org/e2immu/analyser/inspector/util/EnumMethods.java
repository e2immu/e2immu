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

package org.e2immu.analyser.inspector.util;

import org.e2immu.analyser.inspector.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.e2immu.analyser.inspector.TypeInspectionImpl.InspectionState.BY_HAND;

public class EnumMethods {

    public static void create(ExpressionContext expressionContext,
                              TypeInfo enumType,
                              TypeInspectionImpl.Builder builder, List<FieldInfo> enumFields) {
        var primitives = expressionContext.typeContext.getPrimitives();
        var e2 = expressionContext.typeContext.typeMapBuilder.getE2ImmuAnnotationExpressions();

        var notNullContract = E2ImmuAnnotationExpressions.createContract(primitives, e2.notNull);
        var notModifiedContract = E2ImmuAnnotationExpressions.createContract(primitives, e2.notModified);
        var notPropagateMod = E2ImmuAnnotationExpressions.createNegativeContract(primitives, e2.propagateModification);
        var e2Container = E2ImmuAnnotationExpressions.createContract(primitives, e2.e2Container);

        // name()

        var nameBuilder = new MethodInspectionImpl.Builder(enumType, "name")
                .setSynthetic(true)
                .setReturnType(primitives.stringParameterizedType)
                .addModifier(MethodModifier.PUBLIC)
                .addAnnotation(notNullContract)
                .addAnnotation(e2Container)
                .addAnnotation(notModifiedContract);
        nameBuilder.readyToComputeFQN(expressionContext.typeContext);
        expressionContext.typeContext.typeMapBuilder.registerMethodInspection(nameBuilder);
        builder.addMethod(nameBuilder.getMethodInfo());

        // values()

        var arrayInitializer = new ArrayInitializer(expressionContext.typeContext,
                enumFields.stream().map(fieldInfo -> (Expression)
                        new VariableExpression(new FieldReference(expressionContext.typeContext, fieldInfo, null)))
                        .toList(),
                enumType.asParameterizedType(expressionContext.typeContext));
        var valuesReturnType = new ParameterizedType(enumType, 1);
        var returnNewArray = new ReturnStatement(NewObject.withArrayInitialiser(enumType.fullyQualifiedName,
                null,
                valuesReturnType, List.of(), arrayInitializer, new BooleanConstant(primitives, true)));
        var valuesBlock = new Block.BlockBuilder().addStatement(returnNewArray).build();
        var valuesBuilder = new MethodInspectionImpl.Builder(enumType, "values")
                .setSynthetic(true)
                .setReturnType(valuesReturnType)
                .setStatic(true)
                .addModifier(MethodModifier.PUBLIC)
                .setInspectedBlock(valuesBlock);
        valuesBuilder.readyToComputeFQN(expressionContext.typeContext);
        expressionContext.typeContext.typeMapBuilder.registerMethodInspection(valuesBuilder);
        builder.addMethod(valuesBuilder.getMethodInfo());

        // valueOf()

        var valueOfBuilder = new MethodInspectionImpl.Builder(enumType, "valueOf")
                .setSynthetic(true)
                .setReturnType(enumType.asParameterizedType(expressionContext.typeContext))
                .setStatic(true)
                .addModifier(MethodModifier.PUBLIC)
                .addAnnotation(notNullContract)
                .addAnnotation(notModifiedContract);
        var valueOfP0B = new ParameterInspectionImpl.Builder(primitives.stringParameterizedType,
                "name", 0)
                .addAnnotation(notPropagateMod)
                .addAnnotation(e2Container)
                .addAnnotation(notNullContract);
        valueOfBuilder.addParameter(valueOfP0B);
        valueOfBuilder.readyToComputeFQN(expressionContext.typeContext);

        if (insertCode(expressionContext.typeContext)) {
            var codeBlock = returnValueOf(expressionContext, enumType, builder, valuesBuilder,
                    nameBuilder, valueOfBuilder.getParameters().get(0), notModifiedContract);
            valueOfBuilder.setInspectedBlock(codeBlock);
        } else {
            valueOfBuilder.addAnnotation(e2Container);
        }

        expressionContext.typeContext.typeMapBuilder.registerMethodInspection(valueOfBuilder);
        builder.addMethod(valueOfBuilder.getMethodInfo());
    }

    private static final List<Class<?>> TYPES_USED_IN_VALUE_OF = List.of(Arrays.class, Stream.class, Optional.class);

    private static boolean insertCode(TypeContext typeContext) {
        return TYPES_USED_IN_VALUE_OF.stream().allMatch(clazz ->
                typeContext.getFullyQualified(clazz.getCanonicalName(), false) != null);
    }

    /*
     return Arrays.stream(values()).filter(v -> v.name().equals(name)).findFirst().orElseThrow()

     Later we can add a filter
     */
    private static Block returnValueOf(ExpressionContext expressionContext,
                                       TypeInfo enumType,
                                       TypeInspectionImpl.Builder enumTypeBuilder,
                                       MethodInspectionImpl.Builder valuesMethod,
                                       MethodInspectionImpl.Builder nameMethod,
                                       ParameterInfo nameParameter,
                                       AnnotationExpression notModifiedContract) {
        var typeContext = expressionContext.typeContext;

        var enumTypeExpression = new TypeExpression(valuesMethod.getMethodInfo().typeInfo
                .asParameterizedType(typeContext), Diamond.NO);
        var values = new MethodCall(true, enumTypeExpression,
                valuesMethod.getMethodInfo(), valuesMethod.getReturnType(), List.of());
        var arrays = typeContext.getFullyQualified(Arrays.class);
        var streamArray = arrays.typeInspection.get().methodStream(TypeInspection.Methods.THIS_TYPE_ONLY)
                .filter(m -> "stream".equals(m.name) && m.methodInspection.get().getParameters().size() == 1 &&
                        m.methodInspection.get().getTypeParameters().size() == 1).findFirst().orElseThrow();
        var arraysType = new TypeExpression(arrays.asParameterizedType(typeContext), Diamond.NO);
        var callStream = new MethodCall(arraysType, streamArray, List.of(values));

        var functionalInterfaceType = enumPredicate(typeContext, enumType);
        var predicateBuilder = predicate(functionalInterfaceType,
                expressionContext, enumType, enumTypeBuilder, notModifiedContract, nameMethod, nameParameter);
        var implementationMethod = predicateBuilder.getMethodInfo()
                .typeInfo.asParameterizedType(typeContext);
        var lambda = new Lambda(typeContext, functionalInterfaceType, implementationMethod, List.of(Lambda.OutputVariant.EMPTY));

        var stream = typeContext.getFullyQualified(Stream.class);
        var filter = stream.findUniqueMethod("filter", 1);
        var callFilter = new MethodCall(callStream, filter, List.of(lambda));

        var findFirst = stream.findUniqueMethod("findFirst", 0);
        var callFindFirst = new MethodCall(callFilter, findFirst, List.of());

        var optional = typeContext.getFullyQualified(Optional.class);
        var orElseThrow = optional.findUniqueMethod("orElseThrow", 0);
        var callOrElseThrow = new MethodCall(callFindFirst, orElseThrow, List.of());
        return new Block.BlockBuilder().addStatement(new ReturnStatement(callOrElseThrow)).build();
    }

    private static ParameterizedType enumPredicate(TypeContext typeContext, TypeInfo enumType) {
        var predicate = typeContext.getFullyQualified(Predicate.class);
        return new ParameterizedType(predicate, List.of(enumType.asParameterizedType(typeContext)));
    }

    private static MethodInspectionImpl.Builder predicate(ParameterizedType functionalInterfaceType,
                                                          ExpressionContext expressionContext,
                                                          TypeInfo enumType,
                                                          TypeInspectionImpl.Builder enumTypeBuilder,
                                                          AnnotationExpression notModifiedContract,
                                                          MethodInspectionImpl.Builder nameMethod,
                                                          ParameterInfo nameParameter) {
        var primitives = expressionContext.typeContext.getPrimitives();
        var enumPt = enumType.asParameterizedType(expressionContext.typeContext);

        var lambdaType = new TypeInfo(enumType,
                expressionContext.anonymousTypeCounters.newIndex(expressionContext.primaryType));
        var builder = expressionContext.typeContext.typeMapBuilder.add(lambdaType, BY_HAND);
        builder.setTypeNature(TypeNature.CLASS)
                .setSynthetic(true)
                .addInterfaceImplemented(functionalInterfaceType)
                .setParentClass(primitives.objectParameterizedType);

        var predicate = new MethodInspectionImpl.Builder(lambdaType, "test")
                .setSynthetic(true)
                .setReturnType(primitives.booleanParameterizedType)
                .addModifier(MethodModifier.PUBLIC)
                .addAnnotation(notModifiedContract);

        var predicate0Builder = new ParameterInspectionImpl.Builder(enumPt, "v", 0);
        predicate.addParameter(predicate0Builder);
        predicate.readyToComputeFQN(expressionContext.typeContext);

        var predicate0 = predicate.getParameters().get(0);
        var codeBlock = returnEquals(expressionContext.typeContext, nameMethod, nameParameter, predicate0);
        predicate.setInspectedBlock(codeBlock);

        expressionContext.typeContext.typeMapBuilder.registerMethodInspection(predicate);
        builder.addMethod(predicate.getMethodInfo());

        enumTypeBuilder.addSubType(lambdaType);
        return predicate;
    }

    // v.name().equals(name)
    private static Block returnEquals(TypeContext typeContext,
                                      MethodInspectionImpl.Builder nameMethod,
                                      ParameterInfo nameParameter,
                                      ParameterInfo v) {

        var vName = new MethodCall(false,
                new VariableExpression(v), nameMethod.getMethodInfo(), nameMethod.getReturnType(), List.of());

        var object = typeContext.getFullyQualified(Object.class);
        var equals = object.findUniqueMethod("equals", 1);
        var callEquals = new MethodCall(false, vName, equals, equals.returnType(),
                List.of(new VariableExpression(nameParameter)));

        return new Block.BlockBuilder().addStatement(new ReturnStatement(callEquals)).build();
    }

}
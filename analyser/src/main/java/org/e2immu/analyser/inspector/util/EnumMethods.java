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
import org.e2immu.analyser.resolver.SortedType;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.e2immu.analyser.inspector.InspectionState.BY_HAND;

public class EnumMethods {

    public static void create(ExpressionContext expressionContext,
                              TypeInfo enumType,
                              TypeInspectionImpl.Builder builder, List<FieldInfo> enumFields) {
        var typeContext = expressionContext.typeContext();
        var primitives = typeContext.getPrimitives();
        var e2 = typeContext.typeMap.getE2ImmuAnnotationExpressions();

        var notNullContract = E2ImmuAnnotationExpressions.createContract(primitives, e2.notNull);
        var notModifiedContract = E2ImmuAnnotationExpressions.createContract(primitives, e2.notModified);
        var eRContainer = E2ImmuAnnotationExpressions.createContract(primitives, e2.eRContainer);

        // name()

        var nameBuilder = new MethodInspectionImpl.Builder(enumType, "name")
                .setSynthetic(true)
                .setReturnType(primitives.stringParameterizedType())
                .addModifier(MethodModifier.PUBLIC)
                .addModifier(MethodModifier.ABSTRACT) // no code -> shallow method analyser
                .addAnnotation(notNullContract)
                .addAnnotation(eRContainer)
                .addAnnotation(notModifiedContract);
        nameBuilder.readyToComputeFQN(typeContext);
        typeContext.typeMap.registerMethodInspection(nameBuilder);
        builder.addMethod(nameBuilder.getMethodInfo());

        // values()

        var arrayInitializer = new ArrayInitializer(typeContext,
                enumFields.stream().map(fieldInfo -> (Expression)
                                new VariableExpression(new FieldReference(typeContext, fieldInfo, null)))
                        .toList(),
                enumType.asParameterizedType(typeContext));
        var valuesReturnType = new ParameterizedType(enumType, 1);
        var returnNewArray = new ReturnStatement(Identifier.generate(),
                ConstructorCall.withArrayInitialiser(null, valuesReturnType, List.of(), arrayInitializer));
        var valuesBlock = new Block.BlockBuilder(Identifier.generate()).addStatement(returnNewArray).build();
        var valuesBuilder = new MethodInspectionImpl.Builder(enumType, "values")
                .setSynthetic(true)
                .setReturnType(valuesReturnType)
                .setStatic(true)
                .addModifier(MethodModifier.PUBLIC)
                .setInspectedBlock(valuesBlock);
        valuesBuilder.readyToComputeFQN(typeContext);
        typeContext.typeMap.registerMethodInspection(valuesBuilder);
        builder.addMethod(valuesBuilder.getMethodInfo());

        // valueOf()

        var valueOfBuilder = new MethodInspectionImpl.Builder(enumType, "valueOf")
                .setSynthetic(true)
                .setReturnType(enumType.asParameterizedType(typeContext))
                .setStatic(true)
                .addModifier(MethodModifier.PUBLIC)
                .addAnnotation(notNullContract)
                .addAnnotation(notModifiedContract);
        var valueOfP0B = new ParameterInspectionImpl.Builder(Identifier.generate(),
                primitives.stringParameterizedType(), "name", 0)
                .addAnnotation(eRContainer)
                .addAnnotation(notNullContract);
        valueOfBuilder.addParameter(valueOfP0B);
        valueOfBuilder.readyToComputeFQN(typeContext);

        if (insertCode(typeContext)) {
            var codeBlock = returnValueOf(expressionContext, enumType, valuesBuilder,
                    nameBuilder, valueOfBuilder.getParameters().get(0), notModifiedContract);
            valueOfBuilder.setInspectedBlock(codeBlock);
        } else {
            // we have no idea what the immutability will be!
            valueOfBuilder.addModifier(MethodModifier.ABSTRACT); // no code
        }

        typeContext.typeMap.registerMethodInspection(valueOfBuilder);
        builder.addMethod(valueOfBuilder.getMethodInfo());
    }

    private static final List<Class<?>> TYPES_USED_IN_VALUE_OF = List.of(Arrays.class, Stream.class, Optional.class);

    private static boolean insertCode(TypeContext typeContext) {
        return TYPES_USED_IN_VALUE_OF.stream().allMatch(clazz -> typeContext.isKnown(clazz.getCanonicalName()));
    }

    /*
     return Arrays.stream(values()).filter(v -> v.name().equals(name)).findFirst().orElseThrow()

     Later we can add a filter
     */
    private static Block returnValueOf(ExpressionContext expressionContext,
                                       TypeInfo enumType,
                                       MethodInspection.Builder valuesMethod,
                                       MethodInspection.Builder nameMethod,
                                       ParameterInfo nameParameter,
                                       AnnotationExpression notModifiedContract) {
        var typeContext = expressionContext.typeContext();

        var enumTypeExpression = new TypeExpression(valuesMethod.getMethodInfo().typeInfo
                .asParameterizedType(typeContext), Diamond.NO);
        var values = new MethodCall(Identifier.generate(), true, enumTypeExpression,
                valuesMethod.getMethodInfo(), valuesMethod.getReturnType(), List.of());
        var arrays = typeContext.getFullyQualified(Arrays.class);
        var arraysInspection = typeContext.getTypeInspection(arrays);
        var streamArray = arraysInspection.methodStream(TypeInspection.Methods.THIS_TYPE_ONLY)
                .filter(m -> {
                    if ("stream".equals(m.name)) {
                        MethodInspection methodInspection = typeContext.getMethodInspection(m);
                        return methodInspection.getParameters().size() == 1 &&
                                methodInspection.getTypeParameters().size() == 1;
                    }
                    return false;
                }).findFirst().orElseThrow();
        var arraysType = new TypeExpression(arrays.asParameterizedType(typeContext), Diamond.NO);
        var streamArrayInspection = typeContext.getMethodInspection(streamArray);
        var callStream = new MethodCall(Identifier.generate(), false, arraysType, streamArray,
                streamArrayInspection.getReturnType(), List.of(values));

        var functionalInterfaceType = enumPredicate(typeContext, enumType);
        var predicateBuilder = predicate(functionalInterfaceType,
                expressionContext, enumType, notModifiedContract, nameMethod, nameParameter);
        var implementationMethod = predicateBuilder.getMethodInfo()
                .typeInfo.asParameterizedType(typeContext);
        var lambda = new Lambda(Identifier.generate(),
                typeContext, functionalInterfaceType, implementationMethod, List.of(Lambda.OutputVariant.EMPTY));

        var stream = typeContext.getFullyQualified(Stream.class);
        var filter = stream.findUniqueMethod(typeContext, "filter", 1);
        var callFilter = new MethodCall(Identifier.generate(), false, callStream, filter,
                typeContext.getMethodInspection(filter).getReturnType(), List.of(lambda));

        var findFirst = stream.findUniqueMethod(typeContext, "findFirst", 0);
        var callFindFirst = new MethodCall(Identifier.generate(), false, callFilter, findFirst,
                typeContext.getMethodInspection(findFirst).getReturnType(), List.of());

        var optional = typeContext.getFullyQualified(Optional.class);
        var orElseThrow = optional.findUniqueMethod(typeContext, "orElseThrow", 0);
        var callOrElseThrow = new MethodCall(Identifier.generate(), false, callFindFirst, orElseThrow,
                typeContext.getMethodInspection(orElseThrow).getReturnType(), List.of());
        return new Block.BlockBuilder(Identifier.generate())
                .addStatement(new ReturnStatement(Identifier.generate(), callOrElseThrow)).build();
    }

    private static ParameterizedType enumPredicate(TypeContext typeContext, TypeInfo enumType) {
        var predicate = typeContext.getFullyQualified(Predicate.class);
        return new ParameterizedType(predicate, List.of(enumType.asParameterizedType(typeContext)));
    }

    private static MethodInspection.Builder predicate(ParameterizedType functionalInterfaceType,
                                                          ExpressionContext expressionContext,
                                                          TypeInfo enumType,
                                                          AnnotationExpression notModifiedContract,
                                                          MethodInspection.Builder nameMethod,
                                                          ParameterInfo nameParameter) {
        var typeContext = expressionContext.typeContext();
        var primitives = typeContext.getPrimitives();
        var enumPt = enumType.asParameterizedType(typeContext);

        var lambdaType = new TypeInfo(enumType,
                expressionContext.anonymousTypeCounters().newIndex(expressionContext.primaryType()));
        var builder = typeContext.typeMap.add(lambdaType, BY_HAND);
        builder.setTypeNature(TypeNature.CLASS)
                .setSynthetic(true)
                .addInterfaceImplemented(functionalInterfaceType)
                .noParent(primitives);

        var predicate = new MethodInspectionImpl.Builder(lambdaType, "test")
                .setSynthetic(true)
                .setReturnType(primitives.booleanParameterizedType())
                .addModifier(MethodModifier.PUBLIC)
                .addAnnotation(notModifiedContract);

        var predicate0Builder = new ParameterInspectionImpl.Builder(Identifier.generate(), enumPt, "v", 0);
        predicate.addParameter(predicate0Builder);
        predicate.readyToComputeFQN(typeContext);

        var predicate0 = predicate.getParameters().get(0);
        var codeBlock = returnEquals(typeContext, nameMethod, nameParameter, predicate0);
        predicate.setInspectedBlock(codeBlock);

        typeContext.typeMap.registerMethodInspection(predicate);
        builder.addMethod(predicate.getMethodInfo());

        var lambdaTypeResolution = new TypeResolution.Builder()
                .setSortedType(new SortedType(lambdaType, List.of(lambdaType, predicate.getMethodInfo())))
                .build();
        lambdaType.typeResolution.set(lambdaTypeResolution);
        return predicate;
    }

    // v.name().equals(name)
    private static Block returnEquals(TypeContext typeContext,
                                      MethodInspection.Builder nameMethod,
                                      ParameterInfo nameParameter,
                                      ParameterInfo v) {

        var vName = new MethodCall(Identifier.generate(), false,
                new VariableExpression(v), nameMethod.getMethodInfo(), nameMethod.getReturnType(), List.of());

        var object = typeContext.getFullyQualified(Object.class);
        var equals = object.findUniqueMethod(typeContext, "equals", 1);
        var equalsInspection = typeContext.getMethodInspection(equals);
        var callEquals = new MethodCall(Identifier.generate(), false,
                vName, equals, equalsInspection.getReturnType(),
                List.of(new VariableExpression(nameParameter)));

        return new Block.BlockBuilder(Identifier.generate())
                .addStatement(new ReturnStatement(Identifier.generate(), callEquals)).build();
    }

}
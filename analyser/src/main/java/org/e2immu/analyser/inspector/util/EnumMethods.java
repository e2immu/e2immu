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
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Primitives;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EnumMethods {

    public static void create(ExpressionContext expressionContext,
                              TypeInfo typeInfo,
                              TypeInspectionImpl.Builder builder, List<FieldInfo> enumFields) {
        Primitives primitives = expressionContext.typeContext.getPrimitives();
        E2ImmuAnnotationExpressions e2 = expressionContext.typeContext.typeMapBuilder.getE2ImmuAnnotationExpressions();

        AnnotationExpression notNullContract = E2ImmuAnnotationExpressions.createContract(primitives, e2.notNull);
        AnnotationExpression notModifiedContract = E2ImmuAnnotationExpressions.createContract(primitives, e2.notModified);
        AnnotationExpression notPropagateMod = E2ImmuAnnotationExpressions.createNegativeContract(primitives, e2.propagateModification);
        AnnotationExpression e2Container = E2ImmuAnnotationExpressions.createContract(primitives, e2.e2Container);

        // name()

        MethodInspectionImpl.Builder nameBuilder = new MethodInspectionImpl.Builder(typeInfo, "name")
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

        ArrayInitializer arrayInitializer = new ArrayInitializer(expressionContext.typeContext,
                enumFields.stream().map(fieldInfo -> new VariableExpression(new FieldReference(expressionContext.typeContext,
                        fieldInfo, null))).collect(Collectors.toUnmodifiableList()),
                typeInfo.asParameterizedType(expressionContext.typeContext));
        ParameterizedType valuesReturnType = new ParameterizedType(typeInfo, 1);
        ReturnStatement returnNewArray = new ReturnStatement(NewObject.withArrayInitialiser(typeInfo.fullyQualifiedName,
                null,
                valuesReturnType, List.of(), arrayInitializer, new BooleanConstant(primitives, true)));
        Block valuesBlock = new Block.BlockBuilder().addStatement(returnNewArray).build();
        MethodInspectionImpl.Builder valuesBuilder = new MethodInspectionImpl.Builder(typeInfo, "values")
                .setSynthetic(true)
                .setReturnType(valuesReturnType)
                .setStatic(true)
                .addModifier(MethodModifier.PUBLIC)
                .setInspectedBlock(valuesBlock);
        valuesBuilder.readyToComputeFQN(expressionContext.typeContext);
        expressionContext.typeContext.typeMapBuilder.registerMethodInspection(valuesBuilder);
        builder.addMethod(valuesBuilder.getMethodInfo());

        // valueOf()

        MethodInspectionImpl.Builder valueOfBuilder = new MethodInspectionImpl.Builder(typeInfo, "valueOf")
                .setSynthetic(true)
                .setReturnType(typeInfo.asParameterizedType(expressionContext.typeContext))
                .setStatic(true)
                .addModifier(MethodModifier.PUBLIC)
                .addAnnotation(notNullContract)
                .addAnnotation(notModifiedContract);
        if (insertCode(expressionContext.typeContext)) {
            Block codeBlock = returnValueOf(expressionContext.typeContext, valuesBuilder);
            valueOfBuilder.setInspectedBlock(codeBlock);
        } else {
            valueOfBuilder.addAnnotation(e2Container);
        }
        ParameterInspectionImpl.Builder valueOfP0B = new ParameterInspectionImpl.Builder(primitives.stringParameterizedType,
                "name", 0)
                .addAnnotation(notPropagateMod)
                .addAnnotation(e2Container)
                .addAnnotation(notNullContract);
        valueOfBuilder.addParameter(valueOfP0B);
        valueOfBuilder.readyToComputeFQN(expressionContext.typeContext);
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
    private static Block returnValueOf(TypeContext typeContext, MethodInspectionImpl.Builder valuesMethod) {
        VariableExpression thisVe = new VariableExpression(new This(typeContext, valuesMethod.getMethodInfo().typeInfo));
        MethodCall values = new MethodCall(true, thisVe, valuesMethod.getMethodInfo(), valuesMethod.getReturnType(), List.of());
        TypeInfo arrays = typeContext.getFullyQualified(Arrays.class);
        MethodInfo streamArray = arrays.typeInspection.get().methodStream(TypeInspection.Methods.THIS_TYPE_ONLY)
                .filter(m -> "stream".equals(m.name) && m.methodInspection.get().getParameters().size() == 1 &&
                        m.methodInspection.get().getTypeParameters().size() == 1).findFirst().orElseThrow();
        TypeExpression arraysType = new TypeExpression(arrays.asParameterizedType(typeContext), Diamond.NO);
        MethodCall callStream = new MethodCall(arraysType, streamArray, List.of(values));

        TypeInfo stream = typeContext.getFullyQualified(Stream.class);
        MethodInfo findFirst = stream.findUniqueMethod("findFirst", 0);
        MethodCall callFindFirst = new MethodCall(callStream, findFirst, List.of());

        TypeInfo optional = typeContext.getFullyQualified(Optional.class);
        MethodInfo orElseThrow = optional.findUniqueMethod("orElseThrow", 0);
        MethodCall callOrElseThrow = new MethodCall(callFindFirst, orElseThrow, List.of());
        return new Block.BlockBuilder().addStatement(new ReturnStatement(callOrElseThrow)).build();
    }
}

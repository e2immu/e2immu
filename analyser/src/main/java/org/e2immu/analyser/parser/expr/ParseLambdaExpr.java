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

package org.e2immu.analyser.parser.expr;

import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.LambdaExpr;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.Lambda;
import org.e2immu.analyser.model.expression.UnevaluatedLambdaExpression;
import org.e2immu.analyser.model.expression.UnevaluatedMethodCall;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.parser.ExpressionContext;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.VariableContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.e2immu.analyser.util.Logger.LogTarget.LAMBDA;
import static org.e2immu.analyser.util.Logger.log;

public class ParseLambdaExpr {

    public static Expression parse(ExpressionContext expressionContext, LambdaExpr lambdaExpr, MethodTypeParameterMap singleAbstractMethod) {
        if (singleAbstractMethod == null || !singleAbstractMethod.isSingleAbstractMethod()) {
            return partiallyParse(lambdaExpr);
        }
        log(LAMBDA, "Start parsing lambda {}, single abstract method context {}", lambdaExpr, singleAbstractMethod);

        List<ParameterInfo> parameters = new ArrayList<>();
        VariableContext newVariableContext = VariableContext.dependentVariableContext(expressionContext.variableContext);
        int cnt = 0;
        boolean allDefined = true;
        List<ParameterizedType> types = new ArrayList<>();

        MethodInfo owner = createAnonymousTypeAndApplyMethod(singleAbstractMethod.methodInspection.getMethodInfo().name,
                expressionContext.enclosingType, expressionContext.topLevel.newIndex(expressionContext.enclosingType));

        for (Parameter parameter : lambdaExpr.getParameters()) {
            ParameterizedType parameterType = null;
            if (parameter.getType() != null && !parameter.getType().asString().isEmpty()) {
                parameterType = ParameterizedType.from(expressionContext.typeContext, parameter.getType());
            }
            if (parameterType == null) {
                // the type hint is an interface with exactly one abstract method (not "default", not static)
                parameterType = singleAbstractMethod.getConcreteTypeOfParameter(cnt);
            }
            // cases
            if (parameterType == null) {
                allDefined = false;
                break;
            }
            types.add(parameterType);
            ParameterInfo parameterInfo = new ParameterInfo(owner, parameterType, parameter.getName().asString(), cnt++);
            // parameter analysis will be set later
            parameters.add(parameterInfo);
            newVariableContext.add(parameterInfo);
        }
        if (!allDefined) {
            log(LAMBDA, "End parsing lambda, delaying, not all parameters are defined yet");
            throw new UnsupportedOperationException("Not all parameters defined??");
        }
        ExpressionContext newExpressionContext = expressionContext.newVariableContext(newVariableContext, "lambda");

        Block block;
        ParameterizedType inferredReturnType;

        boolean isExpression = lambdaExpr.getExpressionBody().isPresent();
        if (isExpression) {
            Expression expr = newExpressionContext.parseExpression(lambdaExpr.getExpressionBody());
            if (expr instanceof UnevaluatedMethodCall) {
                log(LAMBDA, "Body results in unevaluated method call, so I can't be evaluated either");
                return partiallyParse(lambdaExpr);
            }
            inferredReturnType = expr.returnType();
            block = new Block.BlockBuilder().addStatement(new ReturnStatement(false, expr)).build();
        } else {
            block = newExpressionContext.parseBlockOrStatement(lambdaExpr.getBody());
            inferredReturnType = block.mostSpecificReturnType(expressionContext.typeContext);
        }
        ParameterizedType functionalType = singleAbstractMethod.inferFunctionalType(types, inferredReturnType);
        TypeInfo anonymousType = continueCreationOfAnonymousType(expressionContext.typeContext,
                expressionContext.enclosingType, functionalType, owner, parameters, block, inferredReturnType);
        log(LAMBDA, "End parsing lambda as block, inferred functional type {}, new type {}", functionalType, anonymousType.fullyQualifiedName);

        expressionContext.addNewlyCreatedType(anonymousType);

        return new Lambda(functionalType, anonymousType.asParameterizedType());
    }

    // experimental: we look at the parameters, and return an expression which is superficial, with only
    // the return type as functional type of importance
    private static Expression partiallyParse(LambdaExpr lambdaExpr) {
        return new UnevaluatedLambdaExpression(Set.of(lambdaExpr.getParameters().size()), lambdaExpr.getExpressionBody().isPresent() ? true : null);
    }

    private static MethodInfo createAnonymousTypeAndApplyMethod(String name, TypeInfo enclosingType, int nextId) {
        TypeInfo typeInfo = new TypeInfo(enclosingType, nextId);
        return new MethodInfo(typeInfo, name, false);
    }

    private static TypeInfo continueCreationOfAnonymousType(InspectionProvider inspectionProvider,
                                                            TypeInfo enclosingType,
                                                            ParameterizedType functionalInterfaceType,
                                                            MethodInfo methodInfo,
                                                            List<ParameterInfo> parameters,
                                                            Block block,
                                                            ParameterizedType returnType) {
        MethodInspectionImpl.Builder methodInspectionBuilder = new MethodInspectionImpl.Builder(methodInfo);
        MethodTypeParameterMap sam = functionalInterfaceType.findSingleAbstractMethodOfInterface(inspectionProvider);
        ParameterizedType bestReturnType = returnType.mostSpecific(inspectionProvider,
                sam.methodInspection.getMethodInfo().returnType());
        methodInspectionBuilder.setReturnType(Objects.requireNonNull(bestReturnType));
        methodInspectionBuilder.addParameters(parameters);
        methodInspectionBuilder.setBlock(block);
        methodInfo.methodInspection.set(methodInspectionBuilder.build());

        TypeInfo typeInfo = methodInfo.typeInfo;
        TypeInspectionImpl.Builder typeInspectionBuilder = new TypeInspectionImpl.Builder(typeInfo, TypeInspectionImpl.BY_HAND);
        typeInspectionBuilder.setParentClass(inspectionProvider.getPrimitives().objectParameterizedType);
        typeInspectionBuilder.setTypeNature(TypeNature.CLASS);
        typeInspectionBuilder.addInterfaceImplemented(functionalInterfaceType);
        typeInspectionBuilder.setEnclosingType(enclosingType);
        typeInspectionBuilder.addMethod(methodInfo);


        typeInfo.typeInspection.set(typeInspectionBuilder.build());
        return typeInfo;
    }
}
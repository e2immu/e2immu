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

package org.e2immu.analyser.inspector.expr;

import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.LambdaExpr;
import org.e2immu.analyser.inspector.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.Lambda;
import org.e2immu.analyser.model.expression.UnevaluatedLambdaExpression;
import org.e2immu.analyser.model.expression.UnevaluatedMethodCall;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.TypeMapImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.e2immu.analyser.inspector.TypeInspectionImpl.InspectionState.BY_HAND;
import static org.e2immu.analyser.util.Logger.LogTarget.LAMBDA;
import static org.e2immu.analyser.util.Logger.log;

public class ParseLambdaExpr {

    public static Expression parse(ExpressionContext expressionContext,
                                   LambdaExpr lambdaExpr,
                                   MethodTypeParameterMap singleAbstractMethod) {
        if (singleAbstractMethod == null || !singleAbstractMethod.isSingleAbstractMethod()) {
            return partiallyParse(lambdaExpr, expressionContext.getLocation());
        }
        log(LAMBDA, "Start parsing lambda {}, single abstract method context {}", lambdaExpr, singleAbstractMethod);

        VariableContext newVariableContext = VariableContext.dependentVariableContext(expressionContext.variableContext);
        int cnt = 0;
        boolean allDefined = true;
        List<ParameterizedType> types = new ArrayList<>();
        InspectionProvider inspectionProvider = expressionContext.typeContext;

        MethodInspectionImpl.Builder applyMethodInspectionBuilder =
                createAnonymousTypeAndApplyMethod(singleAbstractMethod.methodInspection.getMethodInfo().name,
                        expressionContext.enclosingType,
                        expressionContext.anonymousTypeCounters.newIndex(expressionContext.primaryType));
        List<Lambda.OutputVariant> outputVariants = new ArrayList<>(lambdaExpr.getParameters().size());

        for (Parameter parameter : lambdaExpr.getParameters()) {
            ParameterizedType parameterType = null;
            Lambda.OutputVariant outputVariant;
            if (parameter.getType() != null) {
                String typeAsString = parameter.getType().asString();
                outputVariant = "var".equals(typeAsString) ? Lambda.OutputVariant.VAR :
                        !typeAsString.isEmpty() ? Lambda.OutputVariant.TYPED : Lambda.OutputVariant.EMPTY;
                if (outputVariant == Lambda.OutputVariant.TYPED) {
                    parameterType = ParameterizedTypeFactory.from(expressionContext.typeContext, parameter.getType());
                }
            } else {
                outputVariant = Lambda.OutputVariant.EMPTY;
            }
            outputVariants.add(outputVariant);

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
            ParameterInspectionImpl.Builder parameterBuilder =
                    new ParameterInspectionImpl.Builder(parameterType, parameter.getName().asString(), cnt++);
            // parameter analysis will be set later
            applyMethodInspectionBuilder.addParameter(parameterBuilder);
        }
        if (!allDefined) {
            log(LAMBDA, "End parsing lambda, delaying, not all parameters are defined yet");
            throw new UnsupportedOperationException("Not all parameters defined??");
        }

        // we've added name and parameters, so we're ready to build the FQN and make the parameters immutable
        applyMethodInspectionBuilder.readyToComputeFQN(inspectionProvider);
        applyMethodInspectionBuilder.makeParametersImmutable();
        applyMethodInspectionBuilder.getParameters().forEach(newVariableContext::add);

        TypeInfo anonymousType = applyMethodInspectionBuilder.owner;
        ExpressionContext newExpressionContext = expressionContext.newLambdaContext(anonymousType,
                newVariableContext);

        Evaluation evaluation = evaluate(lambdaExpr, newExpressionContext, singleAbstractMethod, inspectionProvider);
        if (evaluation == null) {
            return partiallyParse(lambdaExpr, expressionContext.getLocation());
        }

        ParameterizedType functionalType = singleAbstractMethod.inferFunctionalType(inspectionProvider,
                types, evaluation.inferredReturnType);
        continueCreationOfAnonymousType(expressionContext.typeContext.typeMapBuilder,
                applyMethodInspectionBuilder, functionalType, evaluation.block, evaluation.inferredReturnType);
        log(LAMBDA, "End parsing lambda as block, inferred functional type {}, new type {}",
                functionalType, anonymousType.fullyQualifiedName);

        expressionContext.addNewlyCreatedType(anonymousType);

        return new Lambda(inspectionProvider, functionalType, anonymousType.asParameterizedType(inspectionProvider),
                outputVariants);
    }

    private record Evaluation(Block block, ParameterizedType inferredReturnType) {
    }

    private static Evaluation evaluate(LambdaExpr lambdaExpr,
                                       ExpressionContext newExpressionContext,
                                       MethodTypeParameterMap singleAbstractMethod,
                                       InspectionProvider inspectionProvider) {
        boolean isExpression = lambdaExpr.getExpressionBody().isPresent();
        if (isExpression) {
            Expression expr = lambdaExpr.getExpressionBody()
                    .map(e -> newExpressionContext.parseExpression(e, singleAbstractMethod.getConcreteReturnType(), null))
                    .orElse(EmptyExpression.EMPTY_EXPRESSION);
            if (expr instanceof UnevaluatedMethodCall) {
                log(LAMBDA, "Body results in unevaluated method call, so I can't be evaluated either");
                return null;
            }
            ParameterizedType inferredReturnType = expr.returnType();
            if (Primitives.isVoid(singleAbstractMethod.getConcreteReturnType())) {
                // we don't expect/want a value, even if the inferredReturnType provides one
                return new Evaluation(new Block.BlockBuilder().addStatement(new ExpressionAsStatement(expr)).build(),
                        inferredReturnType);
            } else {
                // but if we expect one, the inferredReturnType cannot be void (would be a compiler error)
                assert !Primitives.isVoid(inferredReturnType);
                return new Evaluation(new Block.BlockBuilder().addStatement(new ReturnStatement(expr)).build(),
                        inferredReturnType);
            }
        } else {
            Block block = newExpressionContext.parseBlockOrStatement(lambdaExpr.getBody());
            return new Evaluation(block, block.mostSpecificReturnType(inspectionProvider));
        }
    }

    // experimental: we look at the parameters, and return an expression which is superficial, with only
    // the return type as functional type of importance
    private static Expression partiallyParse(LambdaExpr lambdaExpr, Location location) {
        return new UnevaluatedLambdaExpression(Set.of(lambdaExpr.getParameters().size()),
                lambdaExpr.getExpressionBody().isPresent() ? true : null, location);
    }

    private static MethodInspectionImpl.Builder createAnonymousTypeAndApplyMethod(String name,
                                                                                  TypeInfo enclosingType, int nextId) {
        TypeInfo typeInfo = new TypeInfo(enclosingType, nextId);
        return new MethodInspectionImpl.Builder(typeInfo, name);
    }

    private static void continueCreationOfAnonymousType(TypeMapImpl.Builder typeMapBuilder,
                                                        MethodInspectionImpl.Builder builder,
                                                        ParameterizedType functionalInterfaceType,
                                                        Block block,
                                                        ParameterizedType returnType) {
        MethodTypeParameterMap sam = functionalInterfaceType.findSingleAbstractMethodOfInterface(typeMapBuilder);
        MethodInspection methodInspectionOfSAMsMethod = typeMapBuilder.getMethodInspection(sam.methodInspection.getMethodInfo());
        ParameterizedType bestReturnType = returnType.mostSpecific(typeMapBuilder,
                methodInspectionOfSAMsMethod.getReturnType());
        builder.setReturnType(Objects.requireNonNull(bestReturnType));
        builder.setInspectedBlock(block);

        MethodInfo methodInfo = builder.getMethodInfo(); // don't build yet!
        typeMapBuilder.registerMethodInspection(builder);

        TypeInfo typeInfo = methodInfo.typeInfo;
        TypeInspectionImpl.Builder typeInspectionBuilder = typeMapBuilder.ensureTypeInspection(typeInfo, BY_HAND);
        typeInspectionBuilder.setParentClass(typeMapBuilder.getPrimitives().objectParameterizedType);
        typeInspectionBuilder.setTypeNature(TypeNature.CLASS);
        typeInspectionBuilder.addInterfaceImplemented(functionalInterfaceType);
        typeInspectionBuilder.addMethod(methodInfo);
        TypeInspection builtTypeInspection = typeInspectionBuilder.build();
        typeInfo.typeInspection.set(builtTypeInspection);
    }
}
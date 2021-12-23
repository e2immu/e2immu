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
import org.e2immu.analyser.model.expression.*;
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

import static org.e2immu.analyser.model.expression.ErasureExpression.MethodStatic.IGNORE;
import static org.e2immu.analyser.util.Logger.LogTarget.LAMBDA;
import static org.e2immu.analyser.util.Logger.log;

public class ParseLambdaExpr {

    public static Expression erasure(ExpressionContext expressionContext,
                                     LambdaExpr asLambdaExpr) {
        // we need to find the generic functional interface which fits
        int parameters = asLambdaExpr.getParameters().size();
        // it is pretty hard to find out if there is a return statement, but simply computationally heavy.
        // we're not doing this at the moment, and decide to issue a serious inspection warning instead, if we cannot
        // determine a method overload because of the void/non-void difference!
        Set<LambdaExpressionErasures.Count> erasures = Set.of(
                new LambdaExpressionErasures.Count(parameters, true, IGNORE),
                new LambdaExpressionErasures.Count(parameters, false, IGNORE));
        log(LAMBDA, "Returning erasure {}", erasures);
        return new LambdaExpressionErasures(erasures, expressionContext.getLocation());
    }

    public static Expression parse(ExpressionContext expressionContext,
                                   LambdaExpr lambdaExpr,
                                   ForwardReturnTypeInfo forwardReturnTypeInfo) {
        InspectionProvider inspectionProvider = expressionContext.typeContext;
        MethodTypeParameterMap singleAbstractMethod = forwardReturnTypeInfo.computeSAM(inspectionProvider);
        assert singleAbstractMethod != null && singleAbstractMethod.isSingleAbstractMethod();

        log(LAMBDA, "Start parsing lambda at {}, {}", lambdaExpr.getBegin(), forwardReturnTypeInfo.toString(inspectionProvider));

        VariableContext newVariableContext = VariableContext.dependentVariableContext(expressionContext.variableContext);
        int cnt = 0;
        boolean allDefined = true;
        List<ParameterizedType> types = new ArrayList<>();

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
                    new ParameterInspectionImpl.Builder(Identifier.from(parameter),
                            parameterType, parameter.getName().asString(), cnt++);
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

        ParameterizedType returnTypeOfLambda = singleAbstractMethod.getConcreteReturnType();
        ForwardReturnTypeInfo newForward = new ForwardReturnTypeInfo(returnTypeOfLambda);

        Evaluation evaluation = evaluate(lambdaExpr, newExpressionContext, newForward, inspectionProvider);
        assert evaluation != null;

        ParameterizedType functionalType = singleAbstractMethod.inferFunctionalType(inspectionProvider,
                types, evaluation.inferredReturnType);
        continueCreationOfAnonymousType(expressionContext.typeContext.typeMapBuilder,
                applyMethodInspectionBuilder, functionalType, evaluation.block, evaluation.inferredReturnType);
        log(LAMBDA, "End parsing lambda as block, inferred functional type {}, new type {}",
                functionalType.detailedString(inspectionProvider), anonymousType.fullyQualifiedName);

        expressionContext.addNewlyCreatedType(anonymousType);

        return new Lambda(Identifier.from(lambdaExpr), inspectionProvider,
                functionalType, anonymousType.asParameterizedType(inspectionProvider), outputVariants);
    }

    private record Evaluation(Block block, ParameterizedType inferredReturnType) {
    }

    private static Evaluation evaluate(LambdaExpr lambdaExpr,
                                       ExpressionContext newExpressionContext,
                                       ForwardReturnTypeInfo forwardReturnTypeInfo,
                                       InspectionProvider inspectionProvider) {
        boolean isExpression = lambdaExpr.getExpressionBody().isPresent();
        if (isExpression) {
            Expression expr = lambdaExpr.getExpressionBody()
                    .map(e -> newExpressionContext.parseExpression(e, forwardReturnTypeInfo))
                    .orElse(EmptyExpression.EMPTY_EXPRESSION);
            if (expr instanceof MethodCallErasure) {
                log(LAMBDA, "Body results in unevaluated method call, so I can't be evaluated either");
                return null;
            }
            Identifier identifier = Identifier.from(lambdaExpr);
            ParameterizedType inferredReturnType = expr.returnType();
            if (forwardReturnTypeInfo.isVoid(newExpressionContext.typeContext)) {
                // we don't expect/want a value, even if the inferredReturnType provides one
                return new Evaluation(new Block.BlockBuilder(identifier)
                        .addStatement(new ExpressionAsStatement(identifier, expr)).build(),
                        inferredReturnType);
            }
            // but if we expect one, the inferredReturnType cannot be void (would be a compiler error)
            if (Primitives.isVoid(inferredReturnType)) {
                throw new UnsupportedOperationException();
            }
            return new Evaluation(new Block.BlockBuilder(identifier)
                    .addStatement(new ReturnStatement(identifier, expr)).build(), inferredReturnType);
        }
        // not an expression, so we must have a block...
        Block block = newExpressionContext.parseBlockOrStatement(lambdaExpr.getBody());
        return new Evaluation(block, block.mostSpecificReturnType(inspectionProvider));
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
        TypeInspectionImpl.Builder typeInspectionBuilder = typeMapBuilder.ensureTypeInspection(typeInfo,
                TypeInspectionImpl.InspectionState.BY_HAND);
        typeInspectionBuilder.noParent(typeMapBuilder.getPrimitives());
        typeInspectionBuilder.setTypeNature(TypeNature.CLASS);
        typeInspectionBuilder.addInterfaceImplemented(functionalInterfaceType);
        typeInspectionBuilder.addMethod(methodInfo);
        TypeInspection builtTypeInspection = typeInspectionBuilder.build();
        typeInfo.typeInspection.set(builtTypeInspection);
    }
}
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
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import org.e2immu.analyser.inspector.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.Lambda;
import org.e2immu.analyser.model.expression.LambdaExpressionErasures;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.TypeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ParseLambdaExpr {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParseLambdaExpr.class);

    public static Expression erasure(ExpressionContext expressionContext,
                                     LambdaExpr asLambdaExpr) {
        // we need to find the generic functional interface which fits
        int parameters = asLambdaExpr.getParameters().size();
        Set<LambdaExpressionErasures.Count> erasures;
        IsVoid isVoid = computeIsVoid(asLambdaExpr);
        if (isVoid == IsVoid.NO_IDEA || isVoid == IsVoid.ESCAPE) {
            erasures = Set.of(
                    new LambdaExpressionErasures.Count(parameters, true),
                    new LambdaExpressionErasures.Count(parameters, false));
        } else {
            erasures = Set.of(new LambdaExpressionErasures.Count(parameters, isVoid == IsVoid.YES));
        }
        LOGGER.debug("Returning erasure {}", erasures);
        return new LambdaExpressionErasures(erasures, expressionContext.getLocation());
    }

    private enum IsVoid {NO_IDEA, YES, NO, ESCAPE}

    /*
     See example MethodCall_25, where we need to determine a method overload because of the void/non-void difference!
     */
    private static IsVoid computeIsVoid(LambdaExpr asLambdaExpr) {
        if (asLambdaExpr.getBody() instanceof ExpressionStmt expressionStmt) {
            // void expressions: void method call, but we don't have the machinery yet... luckily,
            // the Java compiler cannot handle this neither IF in direct competition (see MethodCall_25, but also _7)
            com.github.javaparser.ast.expr.Expression e = expressionStmt.getExpression();
            if (e.isMethodCallExpr()) return IsVoid.NO_IDEA;
            return IsVoid.NO;
        }
        if (asLambdaExpr.getBody() instanceof BlockStmt) {
            return recursiveComputeIsVoid(asLambdaExpr.getBody());
        }
        throw new UnsupportedOperationException("? either block or expression");
    }

    private static IsVoid recursiveComputeIsVoid(Statement statement) {
        if (statement.isReturnStmt()) {
            ReturnStmt returnStmt = statement.asReturnStmt();
            return returnStmt.getExpression().isEmpty() ? IsVoid.YES : IsVoid.NO;
        }
        if (statement.isBlockStmt()) {
            BlockStmt blockStmt = statement.asBlockStmt();
            int numStatements = blockStmt.getStatements().size();
            if (numStatements == 0) return IsVoid.YES;
            IsVoid resultOfBlock = recursiveComputeIsVoid(blockStmt.getStatement(numStatements - 1));
            if (resultOfBlock == IsVoid.NO_IDEA) return IsVoid.NO;
            return resultOfBlock;
        }
        if (statement.isThrowStmt()) return IsVoid.ESCAPE;
        if (statement.isIfStmt()) return recursiveComputeIsVoid(statement.asIfStmt().getThenStmt());
        if (statement.isTryStmt()) return recursiveComputeIsVoid(statement.asTryStmt().getTryBlock());
        if (statement.isSynchronizedStmt()) return recursiveComputeIsVoid(statement.asSynchronizedStmt().getBody());
        // loop statements not allowed (compilation error)...
        // expression as statement: null
        // throws statement: null
        return null;
    }

    public static Expression parse(ExpressionContext expressionContext,
                                   LambdaExpr lambdaExpr,
                                   ForwardReturnTypeInfo forwardReturnTypeInfo) {
        InspectionProvider inspectionProvider = expressionContext.typeContext();
        MethodTypeParameterMap singleAbstractMethod = forwardReturnTypeInfo.computeSAM(inspectionProvider,
                expressionContext.primaryType());
        assert singleAbstractMethod != null && singleAbstractMethod.isSingleAbstractMethod()
                : "No SAM at " + lambdaExpr.getBegin()
                + "; forward is " + (forwardReturnTypeInfo.type() == null
                ? "NULL" : forwardReturnTypeInfo.type().detailedString(inspectionProvider))
                + "; FI? " + (forwardReturnTypeInfo.type() == null
                ? "NULL" : forwardReturnTypeInfo.type().isFunctionalInterface(inspectionProvider));

        LOGGER.debug("Start parsing lambda at {}, {}", lambdaExpr.getBegin(), forwardReturnTypeInfo.toString(inspectionProvider));

        VariableContext newVariableContext = VariableContext.dependentVariableContext(expressionContext.variableContext());
        int cnt = 0;
        boolean allDefined = true;
        List<ParameterizedType> types = new ArrayList<>();

        MethodInspection.Builder applyMethodInspectionBuilder =
                createAnonymousTypeAndApplyMethod(
                        Identifier.from(lambdaExpr),
                        expressionContext.typeContext(),
                        singleAbstractMethod.methodInspection.getMethodInfo().name,
                        expressionContext.enclosingType(),
                        expressionContext.anonymousTypeCounters().newIndex(expressionContext.primaryType()));
        List<Lambda.OutputVariant> outputVariants = new ArrayList<>(lambdaExpr.getParameters().size());

        for (Parameter parameter : lambdaExpr.getParameters()) {
            ParameterizedType parameterType = null;
            Lambda.OutputVariant outputVariant;
            if (parameter.getType() != null) {
                String typeAsString = parameter.getType().asString();
                outputVariant = "var".equals(typeAsString) ? Lambda.OutputVariant.VAR :
                        !typeAsString.isEmpty() ? Lambda.OutputVariant.TYPED : Lambda.OutputVariant.EMPTY;
                if (outputVariant == Lambda.OutputVariant.TYPED) {
                    parameterType = ParameterizedTypeFactory.from(expressionContext.typeContext(), parameter.getType());
                }
            } else {
                outputVariant = Lambda.OutputVariant.EMPTY;
            }
            outputVariants.add(outputVariant);

            if (parameterType == null) {
                // the type hint is an interface with exactly one abstract method (not "default", not static)
                parameterType = singleAbstractMethod.getConcreteTypeOfParameter(inspectionProvider.getPrimitives(), cnt);
            }
            // cases
            if (parameterType == null) {
                allDefined = false;
                break;
            }
            types.add(parameterType);
            ParameterInspection.Builder parameterBuilder = applyMethodInspectionBuilder
                    .newParameterInspectionBuilder(Identifier.from(parameter),
                            parameterType, parameter.getName().asString(), cnt++);
            // parameter analysis will be set later
            applyMethodInspectionBuilder.addParameter(parameterBuilder);
        }
        if (!allDefined) {
            LOGGER.debug("End parsing lambda, delaying, not all parameters are defined yet");
            throw new UnsupportedOperationException("Not all parameters defined??");
        }

        // we've added name and parameters, so we're ready to build the FQN and make the parameters immutable
        applyMethodInspectionBuilder.readyToComputeFQN(inspectionProvider);
        applyMethodInspectionBuilder.makeParametersImmutable();
        applyMethodInspectionBuilder.getParameters().forEach(newVariableContext::add);

        TypeInfo anonymousType = applyMethodInspectionBuilder.owner();
        ExpressionContext newExpressionContext = expressionContext.newLambdaContext(anonymousType,
                newVariableContext);

        ParameterizedType returnTypeOfLambda = singleAbstractMethod.getConcreteReturnType(inspectionProvider.getPrimitives());

        // add all formal -> concrete of the parameters of the SAM, without the return type
        Map<NamedType, ParameterizedType> extra = new HashMap<>();
        for (ParameterizedType concreteType : types) {
            Map<NamedType, ParameterizedType> map = concreteType.initialTypeParameterMap(inspectionProvider);
            extra.putAll(map);
        }
        ForwardReturnTypeInfo newForward = new ForwardReturnTypeInfo(returnTypeOfLambda, false,
                new TypeParameterMap(extra));

        Evaluation evaluation = evaluate(lambdaExpr, newExpressionContext, newForward, inspectionProvider);

        ParameterizedType functionalType = singleAbstractMethod.inferFunctionalType(inspectionProvider,
                types, evaluation.inferredReturnType);
        continueCreationOfAnonymousType(expressionContext.typeContext().typeMap,
                applyMethodInspectionBuilder, functionalType, evaluation.block,
                evaluation.inferredReturnType,
                expressionContext.enclosingMethod());
        TypeContext typeContext = expressionContext.typeContext();

        expressionContext.resolver().resolve(typeContext,
                typeContext.typeMap.getE2ImmuAnnotationExpressions(), false,
                expressionContext.resolver().storeComments(),
                Map.of(anonymousType, expressionContext.newVariableContext("Lambda")));

        LOGGER.debug("End parsing lambda as block, inferred functional type {}, new type {}",
                functionalType.detailedString(inspectionProvider), anonymousType.fullyQualifiedName);

        return new Lambda(Identifier.from(lambdaExpr), inspectionProvider,
                functionalType, anonymousType.asParameterizedType(inspectionProvider), evaluation.inferredReturnType,
                outputVariants);
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

            Identifier identifier = Identifier.from(lambdaExpr);
            ParameterizedType inferredReturnType = expr.returnType();
            if (forwardReturnTypeInfo.isVoid(newExpressionContext.typeContext())) {
                // we don't expect/want a value, even if the inferredReturnType provides one
                return new Evaluation(new Block.BlockBuilder(identifier)
                        .addStatement(new ExpressionAsStatement(identifier, expr)).build(),
                        inferredReturnType);
            }
            // but if we expect one, the inferredReturnType cannot be void (would be a compiler error)
            if (inferredReturnType.isVoid()) {
                throw new UnsupportedOperationException();
            }
            return new Evaluation(new Block.BlockBuilder(identifier)
                    .addStatement(new ReturnStatement(identifier, expr)).build(), inferredReturnType);
        }
        // not an expression, so we must have a block...
        Block block = newExpressionContext.parseBlockOrStatement(lambdaExpr.getBody());
        return new Evaluation(block, block.mostSpecificReturnType(inspectionProvider, newExpressionContext.primaryType()));
    }


    static MethodInspection.Builder createAnonymousTypeAndApplyMethod(Identifier identifier,
                                                                      InspectionProvider inspectionProvider,
                                                                      String name,
                                                                      TypeInfo enclosingType,
                                                                      int nextId) {
        TypeInfo typeInfo = new TypeInfo(enclosingType, nextId);
        return inspectionProvider.newMethodInspectionBuilder(identifier, typeInfo, name);
    }

    static void continueCreationOfAnonymousType(TypeMap.Builder typeMapBuilder,
                                                MethodInspection.Builder builder,
                                                ParameterizedType functionalInterfaceType,
                                                Block block,
                                                ParameterizedType returnType,
                                                MethodInfo enclosingMethod) {
        MethodTypeParameterMap sam = functionalInterfaceType.findSingleAbstractMethodOfInterface(typeMapBuilder);
        MethodInspection methodInspectionOfSAMsMethod = typeMapBuilder.getMethodInspection(sam.methodInspection.getMethodInfo());
        ParameterizedType bestReturnType = returnType.mostSpecific(typeMapBuilder, builder.owner().primaryType(),
                methodInspectionOfSAMsMethod.getReturnType());
        builder.setReturnType(Objects.requireNonNull(bestReturnType));
        builder.setInspectedBlock(block);

        MethodInfo methodInfo = builder.getMethodInfo(); // don't build yet!

        TypeInfo typeInfo = methodInfo.typeInfo;
        TypeInspection.Builder typeInspectionBuilder = typeMapBuilder.add(typeInfo, InspectionState.BY_HAND)
                .noParent(typeMapBuilder.getPrimitives())
                .setEnclosingMethod(enclosingMethod)
                .setTypeNature(TypeNature.CLASS)
                .addInterfaceImplemented(functionalInterfaceType)
                .addMethod(methodInfo).setFunctionalInterface(true);
        TypeInspection builtTypeInspection = typeInspectionBuilder.build(typeMapBuilder);
        typeInfo.typeInspection.set(builtTypeInspection);
        typeMapBuilder.registerMethodInspection(builder);
    }
}
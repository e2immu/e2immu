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

import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.stmt.ThrowStmt;
import org.e2immu.analyser.inspector.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.ConstructorCall;
import org.e2immu.analyser.model.expression.Lambda;
import org.e2immu.analyser.model.expression.SwitchExpression;
import org.e2immu.analyser.model.expression.util.MultiExpression;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.InspectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ParseSwitchExpr {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParseSwitchExpr.class);

    public static Expression parse(ExpressionContext expressionContext,
                                   SwitchExpr switchExpr,
                                   ForwardReturnTypeInfo forwardReturnTypeInfo) {
        Expression selector = expressionContext.parseExpressionStartVoid(switchExpr.getSelector());

        boolean allEntriesAreExpressions = switchExpr.getEntries().stream()
                .allMatch(entry -> entry.getType() == com.github.javaparser.ast.stmt.SwitchEntry.Type.EXPRESSION
                        || entry.getType() == com.github.javaparser.ast.stmt.SwitchEntry.Type.THROWS_STATEMENT);

        // decide between keeping a switch expression, where each of the cases is an expression,
        // and transforming the switch expression in a lambda/implementation of Function<>
        // where each case block is an if statement, with the yield replaced by return.

        if (allEntriesAreExpressions) {
            ExpressionContext newExpressionContext = expressionContext.newVariableContext("switch-expression");
            addEnumVariables(newExpressionContext, selector);
            List<SwitchEntry> entries = switchExpr.getEntries()
                    .stream()
                    .map(entry -> newExpressionContext.switchEntry(selector, entry))
                    .collect(Collectors.toList());
            MultiExpression yieldExpressions = new MultiExpression(extractExpressionsFromEntries(entries));
            ParameterizedType parameterizedType = yieldExpressions.commonType(expressionContext.typeContext());
            return new SwitchExpression(Identifier.from(switchExpr),
                    selector, entries, parameterizedType, yieldExpressions);
        }
        return createLambda(expressionContext, selector, switchExpr, forwardReturnTypeInfo.type());
    }

    private static void addEnumVariables(ExpressionContext expressionContext, Expression selector) {
        TypeInfo enumType = expressionContext.selectorIsEnumType(selector);
        if (enumType != null) {
            TypeInspection enumInspection = expressionContext.typeContext().getTypeInspection(enumType);
            enumInspection.fields()
                    .forEach(fieldInfo -> expressionContext.variableContext().add(
                            new FieldReference(expressionContext.typeContext(), fieldInfo)));
        }
    }

    private static Expression[] extractExpressionsFromEntries(List<SwitchEntry> entries) {
        return entries.stream().flatMap(e -> extractExpressionFromEntry(e).stream()).toArray(Expression[]::new);
    }

    public static List<Expression> extractExpressionFromEntry(SwitchEntry e) {
        Structure structure = e.structure;
        while (structure.statements() == null && structure.block() != null) {
            structure = structure.block().structure;
        }
        List<Statement> statements = structure.statements();
        if (statements.size() == 1) {
            Statement statement = statements.get(0);
            if (statement instanceof ExpressionAsStatement eas) {
                return List.of(eas.expression);
            }
            if (statement instanceof ThrowStatement throwStatement) {
                // IMPROVE we'll need to mark this somehow, so that the value
                // doesn't become one of the return values in case we'd analyse that
                return List.of(throwStatement.expression);
            }
        }
        throw new UnsupportedOperationException();
    }

    private static Expression createLambda(ExpressionContext expressionContext,
                                           Expression selector,
                                           SwitchExpr switchExpr,
                                           ParameterizedType returnTypeIn) {
        VariableContext newVariableContext = VariableContext.dependentVariableContext(expressionContext.variableContext());
        InspectionProvider inspectionProvider = expressionContext.typeContext();
        ParameterizedType returnType = returnTypeIn.ensureBoxed(inspectionProvider.getPrimitives());
        ParameterizedType selectorType = selector.returnType().ensureBoxed(inspectionProvider.getPrimitives());

        // we start by making an apply method, which takes in the selector,
        // and returns the eventual value
        // it overrides Function<T, R>.apply(T t) returning R
        MethodInspection.Builder applyMethodInspectionBuilder =
                ParseLambdaExpr.createAnonymousTypeAndApplyMethod(Identifier.from(switchExpr),
                        inspectionProvider,
                        "apply",
                        expressionContext.enclosingType(),
                        expressionContext.anonymousTypeCounters().newIndex(expressionContext.primaryType()));

        ParameterInspection.Builder parameterBuilder = applyMethodInspectionBuilder
                .newParameterInspectionBuilder(Identifier.from(switchExpr),
                        selectorType, "selector", 0);
        // parameter analysis will be set later
        applyMethodInspectionBuilder.addParameter(parameterBuilder);

        applyMethodInspectionBuilder.readyToComputeFQN(inspectionProvider);
        applyMethodInspectionBuilder.makeParametersImmutable();
        applyMethodInspectionBuilder.getParameters().forEach(newVariableContext::add);

        TypeInfo anonymousType = applyMethodInspectionBuilder.owner();


        // add all formal -> concrete of the parameters of the SAM, without the return type
        Map<NamedType, ParameterizedType> map = returnType.initialTypeParameterMap(inspectionProvider);
        ForwardReturnTypeInfo newForward = new ForwardReturnTypeInfo(returnType, false,
                new TypeParameterMap(map));

        ExpressionContext newExpressionContext = expressionContext.newSwitchExpressionContext(anonymousType,
                newVariableContext, newForward);
        addEnumVariables(newExpressionContext, selector);

        Block methodBody = constructMethodBody(newExpressionContext, newForward, switchExpr, selector);

        TypeInfo function = expressionContext.typeContext().typeMap.syntheticFunction(1, false);
        ParameterizedType functionalType = new ParameterizedType(function, List.of(selectorType, returnType));

        ParseLambdaExpr.continueCreationOfAnonymousType(expressionContext.typeContext().typeMap,
                applyMethodInspectionBuilder, functionalType, methodBody, returnType);
        TypeContext typeContext = expressionContext.typeContext();

        expressionContext.resolver().resolve(typeContext,
                typeContext.typeMap.getE2ImmuAnnotationExpressions(), false,
                Map.of(anonymousType, expressionContext.newVariableContext("Lambda")));

        LOGGER.debug("End parsing lambda as block, inferred functional type {}, new type {}",
                functionalType.detailedString(expressionContext.typeContext()), anonymousType.fullyQualifiedName);

        List<Lambda.OutputVariant> outputVariants = List.of(Lambda.OutputVariant.EMPTY);
        return new Lambda(Identifier.from(switchExpr), inspectionProvider,
                functionalType, anonymousType.asParameterizedType(inspectionProvider), returnTypeIn, outputVariants);
    }

    private static Block constructMethodBody(ExpressionContext newExpressionContext,
                                             ForwardReturnTypeInfo forwardReturnTypeInfo,
                                             SwitchExpr switchExpr,
                                             Expression selector) {
        Block.BlockBuilder builder = new Block.BlockBuilder(Identifier.from(switchExpr));
        InspectionProvider ip = newExpressionContext.typeContext();

        boolean addedADefault = false;
        for (com.github.javaparser.ast.stmt.SwitchEntry switchEntry : switchExpr.getEntries()) {
            List<Expression> labels = switchEntry.getLabels().stream()
                    .map(newExpressionContext::parseExpressionStartVoid)
                    .collect(Collectors.toList());
            boolean isDefault = labels.isEmpty();
            addedADefault |= isDefault;
            Expression or = SwitchEntry.generateConditionExpression(ip.getPrimitives(), labels, selector);
            Statement statement;
            com.github.javaparser.ast.stmt.SwitchEntry.Type type = switchEntry.getType();
            if (type == com.github.javaparser.ast.stmt.SwitchEntry.Type.EXPRESSION ||
                    type == com.github.javaparser.ast.stmt.SwitchEntry.Type.THROWS_STATEMENT) {
                com.github.javaparser.ast.stmt.Statement st = switchEntry.getStatements().get(0);
                Statement returnStatement;
                if (st.isExpressionStmt()) {
                    com.github.javaparser.ast.expr.Expression e = st.asExpressionStmt().getExpression();
                    Expression expression = newExpressionContext.parseExpression(e, forwardReturnTypeInfo);
                    returnStatement = new ReturnStatement(Identifier.from(e), expression);
                } else if (st.isThrowStmt()) {
                    ThrowStmt throwStmt = st.asThrowStmt();
                    com.github.javaparser.ast.expr.Expression e = throwStmt.getExpression();
                    Expression expression = newExpressionContext.parseExpression(e, forwardReturnTypeInfo);
                    returnStatement = new ThrowStatement(Identifier.from(e), expression);
                } else {
                    throw new UnsupportedOperationException();
                }
                if (isDefault) {
                    statement = returnStatement;
                } else {
                    Block ifBlock = new Block.BlockBuilder(Identifier.from(switchEntry)).addStatement(returnStatement).build();
                    statement = new IfElseStatement(Identifier.from(switchEntry), or, ifBlock, Block.emptyBlock(Identifier.generate("empty switch block")));
                }
            } else if (type == com.github.javaparser.ast.stmt.SwitchEntry.Type.BLOCK) {
                Block exec = newExpressionContext.parseBlockOrStatement(switchEntry.getStatements().get(0));
                if (isDefault) statement = exec;
                else {
                    statement = new IfElseStatement(Identifier.from(switchEntry), or, exec, Block.emptyBlock(Identifier.generate("empty switch entry")));
                }
            } else {
                throw new UnsupportedOperationException("Unknown type " + switchEntry.getType());
            }

            builder.addStatement(statement);
        }
        if (!addedADefault) {
            // no default, so no return statement. To have correct java, we'll add a throws statement
            builder.addStatement(createThrowStatement(newExpressionContext, ip));
        }
        return builder.build();
    }

    private static ThrowStatement createThrowStatement(ExpressionContext newExpressionContext, InspectionProvider ip) {
        TypeInfo runtimeException = newExpressionContext.typeContext().getFullyQualified(RuntimeException.class);
        TypeInspection typeInspection = ip.getTypeInspection(runtimeException);
        MethodInfo constructor = typeInspection.constructors().stream()
                .filter(m -> ip.getMethodInspection(m).getParameters().isEmpty()).findFirst().orElseThrow();
        return new ThrowStatement(Identifier.generate("throw at end of switch"),
                ConstructorCall.objectCreation(Identifier.generate("throw at end of switch object"), constructor,
                        runtimeException.asParameterizedType(ip), Diamond.NO, List.of()));
    }
}

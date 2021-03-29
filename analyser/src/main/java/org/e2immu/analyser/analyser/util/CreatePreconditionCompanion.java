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

package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.AnalysisProvider;
import org.e2immu.analyser.analyser.MethodAnalysisImpl;
import org.e2immu.analyser.inspector.MethodInspectionImpl;
import org.e2immu.analyser.inspector.ParameterInspectionImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public record CreatePreconditionCompanion(InspectionProvider inspectionProvider, AnalysisProvider analysisProvider) {

    public void addPreconditionCompanion(MethodInfo methodInfo, MethodAnalysisImpl.Builder builder, Expression value) {
        // TODO
        // decide if an aspect is in use
        String aspect = preconditionContainsAspect(methodInfo, value);
        CompanionMethodName companionMethodName = new CompanionMethodName(methodInfo.name, CompanionMethodName.Action.PRECONDITION, aspect);
        MethodInfo existingCompanion = methodInfo.methodInspection.get().getCompanionMethods().get(companionMethodName);
        if (existingCompanion == null) {
            MethodInfo companion = createCompanion(companionMethodName, methodInfo, value, aspect);
            builder.addCompanion(companionMethodName, companion);
        }
    }

    private MethodInfo createCompanion(CompanionMethodName companionMethodName, MethodInfo mainMethod, Expression value, String aspect) {
        MethodInspection mainInspection = mainMethod.methodInspection.get();
        MethodInspectionImpl.Builder builder = new MethodInspectionImpl.Builder(mainMethod.typeInfo, companionMethodName.composeMethodName());
        builder.setReturnType(inspectionProvider.getPrimitives().booleanParameterizedType);

        if (aspect != null) {
            builder.addParameter(new ParameterInspectionImpl.Builder(inspectionProvider.getPrimitives().intParameterizedType,
                    aspect, 0).setVarArgs(false));
        }
        int offset = aspect == null ? 0 : 1;
        for (ParameterInfo parameterInfo : mainInspection.getParameters()) {
            builder.addParameter(new ParameterInspectionImpl.Builder(parameterInfo.parameterizedType(),
                    parameterInfo.name, parameterInfo.index + offset).setVarArgs(false));
        }
        builder.readyToComputeFQN(inspectionProvider); // so we can grab the parameters

        Expression expression = parseEvaluatedExpression(value, aspect, mainInspection, builder.getParameters());
        Block block = new Block.BlockBuilder().addStatement(new ReturnStatement(expression)).build();
        builder.setInspectedBlock(block);
        builder.addModifier(MethodModifier.PRIVATE);
        if (mainInspection.isStatic()) builder.addModifier(MethodModifier.STATIC);
        builder.build(inspectionProvider);
        return builder.getMethodInfo();
    }

    // translate the aspect into the first parameter
    // rewire the parameters from the main method to the aspect method
    private Expression parseEvaluatedExpression(Expression value, String aspect, MethodInspection mainInspection, List<ParameterInfo> newParameters) {
        if (aspect != null) {
            MethodInfo aspectMethod = analysisProvider.getTypeAnalysis(mainInspection.getMethodInfo().typeInfo).getAspects().get(aspect);
            MethodCall methodCall = aspectCall(aspectMethod);
            Map<Expression, Expression> expressionMap = Map.of(methodCall, new VariableExpression(newParameters.get(0)));
            Map<Variable, Variable> variableMap = mainInspection.getParameters().stream().collect(Collectors.toMap(pi -> pi, pi ->
                    newParameters.get(pi.index + 1)));
            TranslationMap translationMap = new TranslationMap(Map.of(), expressionMap, variableMap, Map.of(), Map.of());

            return value.translate(translationMap);
        }
        return value; // TODO
    }

    // null means: no aspect
    // see if the type has an aspect; if it does, look up the evaluation, and check for presence

    private String preconditionContainsAspect(MethodInfo methodInfo, Expression value) {
        for (Map.Entry<String, MethodInfo> entry : analysisProvider.getTypeAnalysis(methodInfo.typeInfo).getAspects().entrySet()) {
            MethodCall toExpect = aspectCall(entry.getValue());
            AtomicBoolean found = new AtomicBoolean();
            value.visit(element -> {
                if (toExpect.equals(element)) found.set(true);
            });
            if (found.get()) return entry.getKey();
        }
        return null;
    }

    private MethodCall aspectCall(MethodInfo aspectMethod) {
        return new MethodCall(new VariableExpression(new This(inspectionProvider, aspectMethod.typeInfo)),
                aspectMethod, List.of(), ObjectFlow.NO_FLOW);
    }
}

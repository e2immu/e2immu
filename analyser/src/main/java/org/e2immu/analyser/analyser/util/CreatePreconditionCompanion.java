/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
        Block block = new Block.BlockBuilder().addStatement(new ReturnStatement(false, expression)).build();
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
            TranslationMap translationMap = new TranslationMap(Map.of(), expressionMap, variableMap, Map.of());

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

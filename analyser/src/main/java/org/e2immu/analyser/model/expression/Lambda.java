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

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.StatementAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.output.Guide;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.List;
import java.util.Objects;

public class Lambda implements Expression {
    public final MethodInfo methodInfo;
    public final Block block;
    public final List<ParameterInfo> parameters;
    public final ParameterizedType abstractFunctionalType;
    public final ParameterizedType implementation;

    /**
     * @param abstractFunctionalType e.g. java.util.Supplier
     * @param implementation         anonymous type, with single abstract method with implementation block
     */
    public Lambda(InspectionProvider inspectionProvider,
                  ParameterizedType abstractFunctionalType,
                  ParameterizedType implementation) {
        methodInfo = inspectionProvider.getTypeInspection(implementation.typeInfo).methods().get(0);
        MethodInspection methodInspection = inspectionProvider.getMethodInspection(methodInfo);
        this.block = methodInspection.getMethodBody();
        this.parameters = methodInspection.getParameters();

        assert abstractFunctionalType.isFunctionalInterface(inspectionProvider);
        this.abstractFunctionalType = Objects.requireNonNull(abstractFunctionalType);
        assert implementsFunctionalInterface(inspectionProvider, implementation);
        this.implementation = Objects.requireNonNull(implementation);
    }

    private static boolean implementsFunctionalInterface(InspectionProvider inspectionProvider,
                                                         ParameterizedType parameterizedType) {
        if (parameterizedType.typeInfo == null) return false;
        return inspectionProvider.getTypeInspection(parameterizedType.typeInfo)
                .interfacesImplemented().stream().anyMatch(pt -> pt.isFunctionalInterface(inspectionProvider));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Lambda lambda = (Lambda) o;
        return abstractFunctionalType.equals(lambda.abstractFunctionalType) &&
                implementation.equals(lambda.implementation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(abstractFunctionalType, implementation);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        throw new UnsupportedOperationException();
        //return new Lambda(translationMap.translateType(abstractFunctionalType), translationMap.translateType(implementation));
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return ObjectFlow.NYE;
    }

    private Expression singleExpression() {
        if (block.structure.statements.size() != 1) return null;
        Statement statement = block.structure.statements.get(0);
        if (!(statement instanceof ReturnStatement returnStatement)) return null;
        return returnStatement.expression;
    }

    // this is a functional interface
    @Override
    public ParameterizedType returnType() {
        return abstractFunctionalType;
    }

    @Override
    public OutputBuilder output() {
        OutputBuilder outputBuilder = new OutputBuilder();
        if (parameters.isEmpty()) {
            outputBuilder.add(Symbol.OPEN_CLOSE_PARENTHESIS);
        } else if (parameters.size() == 1) {
            outputBuilder.add(parameters.get(0).output());
        } else {
            outputBuilder.add(Symbol.LEFT_PARENTHESIS)
                    .add(parameters.stream().map(ParameterInfo::output).collect(OutputBuilder.joining(Symbol.COMMA)))
                    .add(Symbol.RIGHT_PARENTHESIS);
        }
        outputBuilder.add(Symbol.LAMBDA);

        Expression singleExpression = singleExpression();
        if (singleExpression != null) {
            outputBuilder.add(outputInParenthesis(precedence(), singleExpression));
        } else {
            Guide.GuideGenerator guideGenerator = Guide.generatorForBlock();
            outputBuilder.add(Symbol.LEFT_BRACE);
            outputBuilder.add(guideGenerator.start());
            StatementAnalysis firstStatement = methodInfo.methodAnalysis.get().getFirstStatement().followReplacements();
            Block.statementsString(outputBuilder, guideGenerator, firstStatement);
            outputBuilder.add(guideGenerator.end());
            outputBuilder.add(Symbol.RIGHT_BRACE);
        }
        return outputBuilder;
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public Precedence precedence() {
        return Precedence.BOTTOM;
    }

    // TODO: the "true" in the types referenced of the parameter is not necessarily true (probably even false!)
    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return UpgradableBooleanMap.of(
                block.typesReferenced(),
                parameters.stream().flatMap(p -> p.typesReferenced(true).stream()).collect(UpgradableBooleanMap.collector()));
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
        ParameterizedType parameterizedType = methodInfo.typeInfo.asParameterizedType(evaluationContext.getAnalyserContext());
        Location location = evaluationContext.getLocation(this);
        ObjectFlow objectFlow = builder.createInternalObjectFlow(location, parameterizedType, Origin.NEW_OBJECT_CREATION);
        Expression result = null;

        MethodAnalysis methodAnalysis = evaluationContext.getMethodAnalysis(methodInfo);
        Expression srv = methodAnalysis.getSingleReturnValue();
        if (srv != null) {
            InlinedMethod inlineValue = srv.asInstanceOf(InlinedMethod.class);
            if (inlineValue != null) {
                result = inlineValue;
            }
        }
        if (result == null) {
            result = new NewObject(evaluationContext.getPrimitives(), parameterizedType, objectFlow);
        }

        builder.setExpression(result);
        return builder.build();
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(block);
    }

    // TODO should we add the parameters to variables() ??
}

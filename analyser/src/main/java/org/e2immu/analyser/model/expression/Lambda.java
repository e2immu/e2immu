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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.InlineValue;
import org.e2immu.analyser.model.abstractvalue.Instance;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
    public Lambda(@NotNull ParameterizedType abstractFunctionalType,
                  @NotNull ParameterizedType implementation) {
        methodInfo = implementation.typeInfo.typeInspection.get().methods().get(0);
        this.block = methodInfo.methodInspection.get().getMethodBody();
        this.parameters = methodInfo.methodInspection.get().getParameters();
        if (!abstractFunctionalType.isFunctionalInterface()) throw new UnsupportedOperationException();
        this.abstractFunctionalType = Objects.requireNonNull(abstractFunctionalType);
        if (!implementsFunctionalInterface(implementation)) throw new UnsupportedOperationException();
        this.implementation = Objects.requireNonNull(implementation);
    }

    private static boolean implementsFunctionalInterface(ParameterizedType parameterizedType) {
        if (parameterizedType.typeInfo == null) return false;
        return parameterizedType.typeInfo.typeInspection.get().interfacesImplemented().stream().anyMatch(ParameterizedType::isFunctionalInterface);
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
    public String expressionString(int indent) {
        String blockString;
        Expression singleExpression = singleExpression();
        if (singleExpression != null) {
            blockString = singleExpression.expressionString(indent);
        } else {
            if (block.structure.statements.isEmpty()) blockString = "{ }";
            else {
                StatementAnalysis firstStatement = methodInfo.methodAnalysis.get().getFirstStatement().followReplacements();
                blockString = block.statementString(indent, firstStatement);
            }
        }
        if (parameters.size() == 1) {
            return parameters.get(0).stream() + " -> " + blockString;
        }
        return "(" + parameters.stream().map(ParameterInfo::stream).collect(Collectors.joining(", ")) + ")"
                + " -> " + blockString;
    }

    @Override
    public int precedence() {
        return 16;
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
        ParameterizedType parameterizedType = methodInfo.typeInfo.asParameterizedType();
        Location location = evaluationContext.getLocation(this);
        ObjectFlow objectFlow = builder.createInternalObjectFlow(location, parameterizedType, Origin.NEW_OBJECT_CREATION);
        Value result = null;

        MethodAnalysis methodAnalysis = evaluationContext.getMethodAnalysis(methodInfo);
        Value srv = methodAnalysis.getSingleReturnValue();
        if (srv != null) {
            InlineValue inlineValue = srv.asInstanceOf(InlineValue.class);
            if (inlineValue != null) {
                result = inlineValue;
            }
        }
        if (result == null) {
            result = new Instance(parameterizedType, objectFlow, UnknownValue.EMPTY);
        }

        builder.setValue(result);
        return builder.build();
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(block);
    }

    // TODO should we add the parameters to variables() ??
}

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
import org.e2immu.analyser.model.abstractvalue.TypeValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotNull;

import java.util.Objects;

@E2Container
public class TypeExpression implements Expression {
    public final ParameterizedType parameterizedType;

    public TypeExpression(@NotNull ParameterizedType parameterizedType) {
        this.parameterizedType = Objects.requireNonNull(parameterizedType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypeExpression that = (TypeExpression) o;
        return parameterizedType.equals(that.parameterizedType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameterizedType);
    }

    @Override
    public ParameterizedType returnType() {
        return parameterizedType;
    }

    @Override
    public String expressionString(int indent) {
        return parameterizedType.stream(); // TODO but there could be occasions where we need the FQN
    }

    @Override
    public int precedence() {
        return 17;
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return parameterizedType.typesReferenced(true);
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
        ObjectFlow objectFlow = builder.createLiteralObjectFlow(parameterizedType);
        return builder.setValue(new TypeValue(parameterizedType, objectFlow)).build();
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new TypeExpression(translationMap.translateType(parameterizedType));
    }
}

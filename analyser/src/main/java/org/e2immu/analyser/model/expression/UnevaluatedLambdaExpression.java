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

import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Set;

@E2Immutable
public class UnevaluatedLambdaExpression implements Expression {
    public final Set<Integer> numberOfParameters; // the amount of parameters of the lambda
    public final Boolean nonVoid; // true when expression without block, null otherwise

    public UnevaluatedLambdaExpression(Set<Integer> numberOfParameters, Boolean nonVoid) {
        this.numberOfParameters = ImmutableSet.copyOf(numberOfParameters);
        this.nonVoid = nonVoid;
    }

    // this is NOT a functional interface, merely the return type of the lambda
    @Override
    @NotNull
    public ParameterizedType returnType() {
        throw new UnsupportedOperationException();
    }

    @Override
    @NotNull
    public String expressionString(int indent) {
        return "<unevaluated lambda with " + numberOfParameters + " parameters>";
    }

    @Override
    public int precedence() {
        return 16;
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor, ForwardEvaluationInfo forwardEvaluationInfo) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        throw new UnsupportedOperationException();
    }
}

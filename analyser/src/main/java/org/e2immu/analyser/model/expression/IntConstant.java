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
import org.e2immu.analyser.model.value.IntValue;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.NotNull;

@E2Immutable
public class IntConstant implements Expression, Constant<Integer> {
    @Override
    @NotNull
    public ParameterizedType returnType() {
        return Primitives.PRIMITIVES.intParameterizedType;
    }

    @NotNull
    public final int constant;

    public IntConstant(int constant) {
        this.constant = constant;
    }

    @Override
    @NotNull
    public String expressionString(int indent) {
        return Integer.toString(constant);
    }

    @Override
    public int precedence() {
        return 17; // highest
    }

    @Override
    @NotNull
    public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor, ForwardEvaluationInfo forwardEvaluationInfo) {
        return new IntValue(constant, evaluationContext.createLiteralObjectFlow(returnType()));
    }

    @Override
    public Integer getValue() {
        return constant;
    }

    @Override
    public String toString() {
        return expressionString(0);
    }
}
